package engine.util;

public class vec2 {

    public float x;
    public float y;

    public vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }


    public vec2 multiply(float b) { //crossproduct
        vec2 c = new vec2(0,0);
        c.setX(b * this.getX());
        c.setY(b * this.getY());
        return c;
    }

    public vec2 add(vec2 b) {
        vec2 c = new vec2(0,0);
        c.setX(this.getX() + b.getX());
        c.setY(this.getY() + b.getY());
        return c;
    }

    public vec2 add(float b) {
        vec2 c = new vec2(0,0);
        c.setX(this.getX() + b);
        c.setY(this.getY() + b);
        return c;
    }

    public vec2 divide(float b) {
        vec2 c = new vec2(0, 0);
        c.setX(this.getX() / b);
        c.setY(this.getY() / b);
        return c;
    }
}
