# Download Queue

The fastest non-priority download queue.

You can adjust buffer-size, time-limit and capacity.

``` cs
public class SemaphoreExtends
{
    public static SemaphoreExtends Default = MakeDefault();
    public static SemaphoreExtends MakeDefault()
    {
        return new SemaphoreExtends()
        {
            Accept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
            UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.139 Safari/537.36"
        };
    }

    public string Accept = null;
    public string UserAgent = null;
    public string Referer = null;
    public string Cookie = null;
    
    public virtual void RunPass(ref HttpWebRequest request)
    {
        try
        {
            if (Accept != null) request.Accept = Accept;
            if (UserAgent != null) request.UserAgent = UserAgent;
            
            if (Referer != null) request.Referer = Referer;
            if (Cookie != null) request.Headers.Add(HttpRequestHeader.Cookie, Cookie);
        }
        catch { }
    }
}

public class DownloadQueue
{
    public int capacity = 64;
    public List<Tuple<string, string, object, SemaphoreCallBack, SemaphoreExtends>> queue = new List<Tuple<string, string, object, SemaphoreCallBack, SemaphoreExtends>>();
    public List<Tuple<string, HttpWebRequest>> requests = new List<Tuple<string, HttpWebRequest>>();
    public List<string> aborted = new List<string>();
    public List<Thread> threads = new List<Thread>();
    public List<ManualResetEvent> interrupt = new List<ManualResetEvent>();
    public IWebProxy proxy { get; set; }

    public delegate void SemaphoreCallBack(string url, string filename, object obj);
    public delegate void DownloadSizeCallBack(string uri, long size, object obj);
    public delegate void DownloadStatusCallBack(string uri, int size, object obj);
    public delegate void RetryCallBack(string uri, object obj);
    public delegate void ErrorCallBack(string uri, string msg, object obj);

    DownloadSizeCallBack download_callback;
    DownloadStatusCallBack status_callback;
    RetryCallBack retry_callback;
    ErrorCallBack err_callback;

    object notify_lock = new object();
    object shutdown_lock = new object();
    object task_lock = new object();
    volatile bool preempt_take = false;
    
    public DownloadQueue(DownloadSizeCallBack notify_size, DownloadStatusCallBack notify_status, RetryCallBack retry, ErrorCallBack err)
    {
        ServicePointManager.DefaultConnectionLimit = 268435456;
        download_callback = notify_size;
        status_callback = notify_status;
        retry_callback = retry;
        err_callback = err;
        proxy = null;

        for (int i = 0; i < capacity; i++)
        {
            interrupt.Add(new ManualResetEvent(false));
            threads.Add(new Thread(new ParameterizedThreadStart(remote_download_thread_handler)));
            threads.Last().Start(i);
        }
    }
    
    public bool timeout_infinite = false;
    public int timeout_ms = 10000;
    public int buffer_size = 131072;
    public bool shutdown = false;
    
    public int Capacity { get { return capacity; } set { capacity = value; } }
    
    public bool Abort(string url)
    {
        lock (queue)
        {
            for (int i = 0; i < queue.Count; i++)
                if (queue[i].Item1 == url)
                {
                    queue.RemoveAt(i);
                    lock (notify_lock) Notify();
                    break;
                }
        }
        lock (requests)
        {
            foreach (var i in requests)
                if (i.Item1 == url)
                    lock (i.Item2) i.Item2.Abort();
        }
        aborted.Add(url);
        return false;
    }
    
    public void Abort()
    {
        lock (requests)
        {
            lock (shutdown_lock) shutdown = true;

            lock (queue)
            {
                foreach (var vp in queue) try { File.Delete(vp.Item2); } catch { }
                queue.Clear();
            }
            for (int i = requests.Count - 1; i >= 0; i--)
                requests[i].Item2.Abort();
        }
    }
    
    public void Add(string url, string path, object obj, SemaphoreCallBack callback, SemaphoreExtends se = null)
    {
        lock (queue) queue.Add(new Tuple<string, string, object, SemaphoreCallBack, SemaphoreExtends>(url, path, obj, callback, se));
        lock (notify_lock) Notify();
    }
    
    public void Preempt()
    {
        preempt_take = true;
    }
    
    public void Reactivation()
    {
        preempt_take = false;
    }

    private void Notify()
    {
        interrupt.ForEach(x => x.Set());
    }

    private void remote_download_thread_handler(object i)
    {
        int index = (int)i;
        while (true)
        {
            interrupt[index].WaitOne();

            Tuple<string, string, object, SemaphoreCallBack, SemaphoreExtends> job;

            lock (queue)
            {
                if (queue.Count > 0)
                {
                    job = queue[0];
                    queue.RemoveAt(0);
                }
                else
                {
                    interrupt[index].Reset();
                    continue;
                }
            }

            string uri = job.Item1;
            string fileName = job.Item2;
            object obj = job.Item3;
            SemaphoreCallBack callback = job.Item4;
            SemaphoreExtends se = job.Item5;

            int retry_count = 0;
       RETRY:
            if (retry_count > 10)
            {
                lock (err_callback) err_callback(uri, "Many retry. auto deleted in download queue.", obj);
                lock (callback) callback(uri, fileName, obj);
                return;
            }

            if (!uri.StartsWith("http"))
            {
                lock (err_callback) err_callback(uri, "Url Error. not corret url.", obj);
                lock (callback) callback(uri, fileName, obj);
                return;
            }

            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(uri);
            se.RunPass(ref request);

            request.Timeout = timeout_infinite ? Timeout.Infinite : timeout_ms;
            request.KeepAlive = true;
            request.Proxy = proxy;

            lock (requests) requests.Add(new Tuple<string, HttpWebRequest>(uri, request));

            try
            {
                var response = (HttpWebResponse)request.GetResponse();
                var inputStream = response.GetResponseStream();
                var outputStream = File.OpenWrite(fileName);
                    
                byte[] buffer = new byte[buffer_size];
                int bytesRead;
                lock (download_callback) download_callback(uri, response.ContentLength, obj);
                do
                {
                    bytesRead = inputStream.Read(buffer, 0, buffer.Length);
                    outputStream.Write(buffer, 0, bytesRead);
                    lock (status_callback) status_callback(uri, bytesRead, obj);
                    lock (shutdown_lock) if (shutdown) break;
                    if (preempt_take)
                    {
                        while (preempt_take)
                            Thread.Sleep(500);
                    }
                } while (bytesRead != 0);

                outputStream.Close();
                outputStream.Dispose();

                inputStream.Close();
                inputStream.Dispose();

                response.Close();
                response.Dispose();

                lock (shutdown_lock)
                    if (shutdown) {
                        File.Delete(fileName);
                            return;
                    }
            }
            catch (Exception e)
            {
                if (e is WebException we)
                {
                    HttpWebResponse webResponse = (HttpWebResponse)we.Response;
                    if (webResponse != null && webResponse.StatusCode == HttpStatusCode.NotFound)
                    {
                        lock (err_callback) err_callback(uri, "404 error. auto deleted in download queue.", obj);
                        goto END;
                    }
                }
                
                lock (aborted)
                    if (!aborted.Contains(uri))
                    {
                        lock (retry_callback) retry_callback(uri, obj);
                        request.Abort();
                        Thread.Sleep(1000);
                        retry_count++;
                        goto RETRY;
                    }
                    else
                    {
                        File.Delete(fileName);
                        lock (callback) callback(uri, fileName, obj);
                        return;
                    }
            }
         END:

            lock (callback) callback(uri, fileName, obj);
        }
    }
}

```
