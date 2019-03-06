# Console

If you want to use custom console in GUI program, refer to this code.

``` cs
[DllImport("kernel32.dll", SetLastError = true)]
[return: MarshalAs(UnmanagedType.Bool)]
static extern bool AllocConsole();

[DllImport("kernel32.dll", SetLastError = true)]
static extern bool FreeConsole();

[DllImport("kernel32.dll", SetLastError = true)]
public static extern IntPtr GetStdHandle(int nStdHandle);

[DllImport("kernel32.dll", SetLastError = true)]
public static extern bool SetStdHandle(int nStdHandle, IntPtr hHandle);

public const int STD_OUTPUT_HANDLE = -11;
public const int STD_INPUT_HANDLE = -10;
public const int STD_ERROR_HANDLE = -12;

[DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
public static extern IntPtr CreateFile([MarshalAs(UnmanagedType.LPTStr)] string filename,
                                       [MarshalAs(UnmanagedType.U4)]     uint access,
                                       [MarshalAs(UnmanagedType.U4)]     FileShare share,
                                                                         IntPtr securityAttributes,
                                       [MarshalAs(UnmanagedType.U4)]     FileMode creationDisposition,
                                       [MarshalAs(UnmanagedType.U4)]     FileAttributes flagsAndAttributes,
                                                                         IntPtr templateFile);

public const uint GENERIC_WRITE = 0x40000000;
public const uint GENERIC_READ = 0x80000000;

private static void OverrideRedirection()
{
    var hOut = GetStdHandle(STD_OUTPUT_HANDLE);
    var hRealOut = CreateFile("CONOUT$", GENERIC_READ | GENERIC_WRITE, FileShare.Write, IntPtr.Zero, FileMode.OpenOrCreate, 0, IntPtr.Zero);
    if (hRealOut != hOut)
    {
        SetStdHandle(STD_OUTPUT_HANDLE, hRealOut);
        System.Console.SetOut(new StreamWriter(System.Console.OpenStandardOutput(), System.Console.OutputEncoding) { AutoFlush = true });
        System.Console.SetIn(new StreamReader(System.Console.OpenStandardInput(), System.Console.InputEncoding));
    }
}

[DllImport("kernel32.dll")]
static extern IntPtr GetConsoleWindow();

[DllImport("user32.dll")]
static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

const int SW_HIDE = 0;
const int SW_SHOW = 5;

private const int MF_BYCOMMAND = 0x00000000;
public const int SC_CLOSE = 0xF060;
internal const UInt32 MF_GRAYED = 0x00000001;

[DllImport("user32.dll")]
public static extern int DeleteMenu(IntPtr hMenu, int nPosition, int wFlags);

[DllImport("user32.dll")]
private static extern IntPtr GetSystemMenu(IntPtr hWnd, bool bRevert);

[DllImport("user32.dll", SetLastError = true)]
static extern IntPtr SetParent(IntPtr hWndChild, IntPtr hWndNewParent);

[DllImport("User32.dll")]
public static extern int SetWindowLong(IntPtr hWnd, int nIndex, int dwNewLong);
[DllImport("User32.dll")]
public static extern int GetWindowLong(IntPtr hWnd, int nIndex);

private const int WS_EX_APPWINDOW = 0x40000;
private const int GWL_EXSTYLE = -0x14;
private const int WS_EX_TOOLWINDOW = 0x0080;

public Console()
{
    // https://stackoverflow.com/questions/4362111/how-do-i-show-a-console-output-window-in-a-forms-application
    AllocConsole();
    // https://stackoverflow.com/questions/15578540/allocconsole-not-printing-when-in-visual-studio
    OverrideRedirection();

    DeleteMenu(GetSystemMenu(GetConsoleWindow(), false), SC_CLOSE, MF_BYCOMMAND);
    System.Console.CancelKeyPress += new ConsoleCancelEventHandler(Console_CancelKeyPress);

    var Handle = GetConsoleWindow();
    ShowWindow(Handle, SW_HIDE);
    SetWindowLong(Handle, GWL_EXSTYLE, GetWindowLong(Handle, GWL_EXSTYLE) | WS_EX_TOOLWINDOW);
    ShowWindow(Handle, SW_SHOW);
}

Thread console_thread;
public void Start()
{
    console_thread = new Thread(Loop);
    console_thread.Start();
}

[SecurityPermissionAttribute(SecurityAction.Demand, ControlThread = true)]
public void Stop()
{
    PromptToken?.Cancel();
    ConsoleToken?.Cancel();
    PromptThread?.Abort();
    console_thread?.Abort();
    FreeConsole();
}

public void Hide()
{
    var handle = GetConsoleWindow();
    ShowWindow(handle, SW_HIDE);
}

public void Show()
{
    var handle = GetConsoleWindow();
    ShowWindow(handle, SW_SHOW);
}

public void Prompt()
{
    System.Console.Out.Write("$ ");

    commandLine = System.Console.In.ReadLine();
}

public string commandLine;
public Task PromptTask;
public CancellationTokenSource PromptToken;
public Thread PromptThread;
public Task ConsoleTask;
public CancellationTokenSource ConsoleToken;

public void Loop()
{
    while (true)
    {
        PromptThread = new Thread(Prompt);
        PromptThread.Start();
        PromptThread.Join();
        PromptThread = null;

        ConsoleToken = new CancellationTokenSource();
        ConsoleTask = Task.Factory.StartNew(() =>
        {
            if (commandLine == null)
            {
                System.Console.Out.WriteLine("");
                return;
            }

            try
            {
                // Write your own command line parser.
            }
            catch (Exception e)
            {
                System.Console.Out.WriteLine($"Error occurred on processing!");
                System.Console.Out.WriteLine($"Message: {e.Message}");
                System.Console.Out.WriteLine($"StackTrace: {e.StackTrace}");
            }
        }, ConsoleToken.Token);
        ConsoleTask.Wait();
    }
}

```
