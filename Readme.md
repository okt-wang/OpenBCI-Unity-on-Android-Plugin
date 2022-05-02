# OpenBCI-Unity-on-Android-Plugin

## Getting Stared
In `Unity` call
```
    private AndroidJavaClass unityClass;
    private AndroidJavaObject unityActivity;
    private AndroidJavaObject _pluginInstance;
    
    
    public void IntializePlugin(string pluginName)
    {
        unityClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        unityActivity = unityClass.GetStatic<AndroidJavaObject>("currentActivity");
        _pluginInstance = new AndroidJavaObject(pluginName);
        if(_pluginInstance == null)
            Debug.Log("Plugin Error");
        _pluginInstance.CallStatic("receiveUnityActivity", unityActivity);
    }
    
    public void InitGanglion()
    {
        Debug.Log("call Init");
        if (_pluginInstance != null)
        {
            _pluginInstance.Call("Init");
        }
    }
    
    public void StreamData()
    {
        Debug.Log("call StreamData");
        if (_pluginInstance != null)
        {
            _pluginInstance.Call("StreamData");
        }
    }
```
### Build From Source
1. Build this project to a *.aar
2. Open *.aar with compresss software(like WinRAR, 7z), remove `class.jar` in `libs/classes.jar`
3. Put *.aar in `Assets/Pluguins/Android`

