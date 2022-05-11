using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;
using Newtonsoft.Json;

public class GanglionController : MonoBehaviour
{
    private AndroidJavaClass unityClass;
    private AndroidJavaObject unityActivity;
    private AndroidJavaObject _pluginInstance;
    public bool connectionStatus = false;
    public int[] impedanceValues = {0,0,0,0,0};
    public Text eegNum;

    public void IntializePlugin(string pluginName)
    {
        unityClass = new AndroidJavaClass("com.unity3d.player.UnityPlayer");
        unityActivity = unityClass.GetStatic<AndroidJavaObject>("currentActivity");
        _pluginInstance = new AndroidJavaObject(pluginName);
        if(_pluginInstance == null)
            Debug.Log("Plugin Error");
        _pluginInstance.CallStatic("receiveUnityActivity", unityActivity);
    }

    public void ReceiveData(string num)
    {
        // Convert to Dictionary
        var values = JsonConvert.DeserializeObject<Dictionary<string, Double>>(num);
        foreach (KeyValuePair<string, Double> kvp in values)
        {
            Debug.Log($"name = {kvp.Key}, val = {kvp.Value}");
        }
        eegNum.text = num;
    }
    
    public void ReceiveImpedance(string val)
    {
        var values = JsonConvert.DeserializeObject<Dictionary<Int32, Int32>>(val);
        foreach (KeyValuePair<Int32, Int32> kvp in values)
        {
            impedanceValues[kvp.Key] = kvp.Value;
            Debug.Log($"name = {kvp.Key}, val = {kvp.Value}");
        }
        
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
    public void StreamImpedance()
    {
        Debug.Log("call StreamImpedance");
        if (_pluginInstance != null)
        {
            _pluginInstance.Call("StreamImpedance");
        }
    }

    public void GetGanglionStatus()
    {
        if (_pluginInstance != null)
        {
            connectionStatus = _pluginInstance.Get<bool>("mConnected"); 
        }
    }
    // Start is called before the first frame update
    void Start()
    {
        IntializePlugin("com.okt.ganglionplugin.PluginInstance");
        InvokeRepeating("GetGanglionStatus", 0f, 1f);
    }

    // Update is called once per frame
    void Update()
    {
    }
}
