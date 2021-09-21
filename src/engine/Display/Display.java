package engine.Display;

public class Display {

    int screenSizeX;
    int screenSizeY;
    float aspectRatio;
    int fov;

    float distScreen;

    public Display(int screenSizeX, int screenSizeY, int fov) {
        this.screenSizeX = screenSizeX;
        this.screenSizeY = screenSizeY;
        this.aspectRatio = screenSizeX/screenSizeY;
        this.fov = fov;
        this.distScreen = (float) (1/Math.tan(Math.toRadians(this.fov/2)));
    }

    public int getScreenSizeX() {
        return screenSizeX;
    }

    public void setScreenSizeX(int screenSizeX) {
        this.screenSizeX = screenSizeX;
    }

    public int getScreenSizeY() {
        return screenSizeY;
    }

    public void setScreenSizeY(int screenSizeY) {
        this.screenSizeY = screenSizeY;
    }

    public float getAspectRatio() {
        return aspectRatio;
    }

    public void setAspectRatio(float aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    public int getFov() {
        return fov;
    }

    public void setFov(int fov) {
        this.fov = fov;
    }

    public float getDistScreen() {
        return distScreen;
    }

    public void setDistScreen(float distScreen) {
        this.distScreen = distScreen;
    }
}
