package engine.Scene.Geometry;

import engine.util.vec3;

public abstract class SDF {

    protected abstract float distanceFunction(vec3 p);

    protected abstract float boundingVolume(vec3 p);

    protected abstract vec3 GetPosOffset();

    protected abstract vec3 GetRotOffset();

}
