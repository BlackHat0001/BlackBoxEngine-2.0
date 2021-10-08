package engine.render;

import com.aparapi.Kernel;
import com.aparapi.Range;
import com.aparapi.device.*;
import engine.Scene.Light;
import engine.util.vec3;

import java.awt.image.BufferedImage;
import java.util.List;

public class Renderer {

    BufferedImage image;

    final Kernel kernel;

    Range range = Range.create(1920 * 1080);

    public final float[] rgb;

    public float renderTime;

    public Renderer() {
        vec3 camOrigin = new vec3(0, 1, 0);
        vec3 camDir = new vec3(10, 3, 2);
        float maxMarch = 100;
        float contact = 0.01f;
        int reflect = 2;
        int raysteps = 100;
        float iTime = 0;

        Light light = new Light(new vec3(5, 20, 20), new vec3(1f));
        float[] lightArray = new float[]{light.pos.x, light.pos.y, light.pos.z,
        light.dir.x, light.dir.y, light.dir.z, light.specular.x, light.specular.y, light.specular.z,
        light.diffuse.x, light.diffuse.y, light.diffuse.z};

        int lightsLength = 12;

        int width = 1920;
        int height = 1080;

        this.rgb = new float[width * height * 3];


        this.kernel = new RayMarch(camOrigin.vec3tofloat3(), camDir.vec3tofloat3(),
                maxMarch, contact, reflect, raysteps, lightArray, lightsLength, new float[0], new float[0], new float[0], 0,
                new float[0], new float[0], height, width, this.rgb, iTime, new float[0], new int[0], new float[0]
                );

    }

    public void Render() {

        //Device device = Device.bestGPU();
        //range = device.createRange2D(1920, 1080);
        this.kernel.setExplicit(true);

        List<OpenCLDevice> devices = OpenCLDevice.listDevices(Device.TYPE.GPU);
        for(OpenCLDevice device : devices) {
            System.out.println(device.getDeviceId());
        }
        System.out.println(devices.size());

        this.kernel.execute(range);

        this.kernel.get(this.rgb);

        this.kernel.getAccumulatedExecutionTime();

        System.out.println(this.kernel.getExecutionMode());

        this.kernel.dispose();

    }

    public BufferedImage RenderToImage() {


        return null;
    }

}
