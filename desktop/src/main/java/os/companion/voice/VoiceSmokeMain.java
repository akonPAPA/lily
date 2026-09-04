package os.companion.voice;

import com.k2fsa.sherpa.onnx.VersionInfo;

public final class VoiceSmokeMain {

    public static void main(String[] args) {
        System.out.println("Loading sherpa-onnx native library...");
        String version = VersionInfo.getVersion();
        String ort = VersionInfo.getOnnxruntimeVersion();
        System.out.println("sherpa-onnx version : " + version);
        System.out.println("onnxruntime version : " + ort);
        System.out.println("OK — native library loaded successfully.");
    }
}
