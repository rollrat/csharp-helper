# Image Processing

## Image from URL

Loading an image using only UriSource can cause a memory leak.
You can prevent memory leaks by writing code like this.

``` cs
Stream image_stream;

private void Bitmap_DownloadCompleted(object sender, EventArgs e)
{
    image_stream.Close();
    image_stream.Dispose();
}

private void LoadImage(string url)
{
    var wc = WebRequest.Create(url);
    image_stream = wc.GetResponse().GetResponseStream();
    Application.Current.Dispatcher.BeginInvoke(new Action(
    delegate
    {
        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.StreamSource = image_stream;
        bitmap.DownloadCompleted += Bitmap_DownloadCompleted;
        bitmap.EndInit();
        Image.Source = bitmap;
    }));
}
```
