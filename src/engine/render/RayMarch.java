package engine.render;

import com.aparapi.Kernel;


public class RayMarch extends Kernel {
    /*----------------------------/
    /----------Input data---------/
    /----------------------------*/

    //---------Ray March---------
    final float[] co; //Camera Origin
    final float[] cd; //Camera Direction (Normalized)
    final float MAX_MARCH; //Max render dist
    final float CONTACT_DIST; //Ray-Object collision distance
    final int REFLECT_QUANT; //Number of reflections
    final int RAYSTEPS;

    final float iTime;

    //Lighting
    final float[] lightArr;
    final int lightsLength;

    //---------------------------------------*
    //Geometry
    final float[] sdfArr;
    final float[] sdfLength;

    //Mesh vars (Not yet implemented)
    final float[] meshArr; //All meshes
    final int meshLength; //Mesh Organizer

    final int[] objArray; //All objects [objid, isSdf?, sdfid, meshid, textureid, materialid]
    final float[] objData; //Data about objects [objid, position, rotation]

    //Textures
    final float[] textures;
    final float[] texels;

    //Materials
    final float[] materials;

    //Screen Info
    final int height;
    final int width;

    //Output data
    final float[] rgb; //Rendered Pixels RGB

    final float[] depthBuffer;
    final int[] sdfBuffer;

    public RayMarch(float[] co, float[] cd, float MAX_MARCH, float CONTACT_DIST, int REFLECT_QUANT, int raysteps, float[] lightArr,
                    int lightsLength, float[] sdfArr, float[] sdfLength, float[] meshArr, int meshLength, float[] textures,
                    float[] materials, int height, int width, float[] px, float iTime, float[] texels, int[] objArray, float[] objData) {
        this.co = co;
        this.cd = cd;
        this.MAX_MARCH = MAX_MARCH;
        this.CONTACT_DIST = CONTACT_DIST;
        this.REFLECT_QUANT = REFLECT_QUANT;
        this.RAYSTEPS = raysteps;
        this.lightArr = lightArr;
        this.lightsLength = lightsLength;
        this.sdfArr = sdfArr;
        this.sdfLength = sdfLength;
        this.meshArr = meshArr;
        this.meshLength = meshLength;
        this.textures = textures;
        this.materials = materials;
        this.height = height;
        this.width = width;
        this.rgb = px;

        this.depthBuffer = new float[(this.rgb.length * 2) * REFLECT_QUANT];
        this.sdfBuffer = new int[(this.rgb.length * 2) * REFLECT_QUANT];

        this.iTime = iTime;
        this.texels = texels;
        this.objArray = objArray;
        this.objData = objData;

        this.put(this.rgb);
        this.put(this.lightArr);
    }

    @Override
    public void run() {

        final int index = this.getGlobalId();
        final int pickIndex = this.height / 2 * this.width + this.width / 2;

        final float currentx = index % this.width;// - this.width * 0.5F + 0.5F;
        final float currenty = index / this.width;// - this.height * 0.5F + 0.5F;


        Render(currentx, currenty, index);


    }

    /*----------------------------/
    /----------Ray March----------/
    /----------------------------*/

    void Render(final float pix, final float piy, final int pxid) {
        final float pxh = ((this.width * -1.0f) + (pix * 2.0f)) / this.height;
        final float pyh = ((this.width * -1.0f) + (piy * 2.0f)) / this.height;


        //---------------Camera Matrix
        final float wwx_prenormal = this.cd[0] - this.co[0];
        final float wwy_prenormal = this.cd[1] - this.co[1];
        final float wwz_prenormal = this.cd[2] - this.co[2];
        final float wwmod = length(wwx_prenormal, wwy_prenormal, wwz_prenormal);
        final float wwx = wwx_prenormal / wwmod;
        final float wwy = wwy_prenormal / wwmod;
        final float wwz = wwz_prenormal / wwmod;

        final float uux = vec3multiplyX(wwy, wwz, 1.0f);
        final float uuy = vec3multiplyY(wwx, wwz, 0.0f, 0.0f);
        final float uuz = vec3multiplyZ(wwx, wwy, 0.0f, 1.0f);

        final float vvx = vec3multiplyX(uuy, uuz, wwy);
        final float vvy = vec3multiplyY(uux, uuz, wwx, wwz);
        final float vvz = vec3multiplyZ(uux, uuy, wwx, wwy);

        //Calc ray dir from camera matrix
        final float rdx_prenormal = uux * pxh + vvx * pyh + wwx * 2.0f;
        final float rdy_prenormal = uuy * pxh + vvy * pyh + wwy * 2.0f;
        final float rdz_prenormal = uuz * pxh + vvz * pyh + wwz * 2.0f;
        final float rdmod = length(rdx_prenormal, rdy_prenormal, rdz_prenormal);
        float rdx = rdx_prenormal / rdmod;
        float rdy = rdy_prenormal / rdmod;
        float rdz = rdz_prenormal / rdmod;

        //---------------Ray Marching
        float colx = 0;
        float coly = 0;
        float colz = 0;
        float facx = 1;
        float facy = 1;
        float facz = 1;
        float rqx = this.co[0];
        float rqy = this.co[1];
        float rqz = this.co[2];

        //Loop for each reflection
        for (int i = 0; i < REFLECT_QUANT; i++) {
            //Ray March
            final float dist = trace(rqx, rqy, rqz, rdx, rdy, rdz, CONTACT_DIST);

            if(dist < MAX_MARCH) {
                //Ray Setup
                final float posx = rqx + rdx * dist;
                final float posy = rqy + rdy * dist;
                final float posz = rqz + rdz * dist;

                //------------Calculate normal
                final float ex = 0.01f;
                final float ey = 0;

                final float pdist = GetDist(posx, posy, posz);
                final float nx_prenormal = GetDist(posx - ex, posy - ey, posz - ey) - pdist;
                final float ny_prenormal = GetDist(posx - ey, posy - ex, posz - ey) - pdist;
                final float nz_prenormal = GetDist(posx - ey, posy - ey, posz - ex) - pdist;

                final float nmod = length(nx_prenormal, ny_prenormal, nz_prenormal);
                final float norx = nx_prenormal / nmod;
                final float nory = ny_prenormal / nmod;
                final float norz = nz_prenormal / nmod;

                final float nordot = dot(rdx, rdy, rdz, norx, nory, norz);
                final float refx = rdx - (2.0f * nordot);
                final float refy = rdy - (2.0f * nordot);
                final float refz = rdz - (2.0f * nordot);
                //-----------End of normal calc

                /*
                //-----------Triplanar Mapping
                texture(pix, piy, sdfBuffer[pix * piy + i], posy, posz);
                final float xr = texels[pix * piy];
                final float xb = texels[pix * piy + 1];
                final float xg = texels[pix * piy + 2];
                final float xa = texels[pix * piy + 3];

                texture(pix, piy, sdfBuffer[pix * piy + i], posz, posy);
                final float yr = texels[pix * piy];
                final float yb = texels[pix * piy + 1];
                final float yg = texels[pix * piy + 2];
                final float ya = texels[pix * piy + 3];

                texture(pix, piy, sdfBuffer[pix * piy + i], posx, posy);
                final float zr = texels[pix * piy];
                final float zb = texels[pix * piy + 1];
                final float zg = texels[pix * piy + 2];
                final float za = texels[pix * piy + 3];

                //Factor blending
                final float sharpBlending = 1;
                final float wx = (float) Math.pow(Math.abs(norx), sharpBlending);
                final float wy = (float) Math.pow(Math.abs(nory), sharpBlending);
                final float wz = (float) Math.pow(Math.abs(norz), sharpBlending);

                final float cr = (xr * wx + yr * wy + zr * wz) / (wx + wy + wz);
                final float cg = (xg * wx + yb * wy + zb * wz) / (wx + wy + wz);
                final float cb = (xb * wx + yg * wy + zg * wz) / (wx + wy + wz);
                final float ca = (xa * wx + ya * wy + za * wz) / (wx + wy + wz);
                //-----------End of Triplanar Mapping

                 */

                //-----------Ambient Occlusion
                float occo = 0.0f;
                float occs = 0.0005f;
                float occw = 0.1f;

                for(int o = 0; o < 15; o++) {
                    final float occd = GetDist(posx + (norx * occs), posy + (nory * occs), posz + (norz * occs));
                    occo += (occs - occd)*occw;
                    occw *= 0.98;
                    occs += occs/(o + 1);
                }
                final float occ = 1.0f - clamp(occo, 0.0f, 1.0f);
                //-----------End of Ambient Occlusion

                float lumx = 0;
                float lumy = 0;
                float lumz = 0;
                final float maaS = 0.5f; //Need to do materials
                final float maaD = 0.5f;
                final float maaA = 0.5f;
                float maaALPHA = 1.0f;
                final float maaX = 0.55f;
                final float maaY = 0.55f;
                final float maaZ = 0.5f;
                final float maaW = 0.2f;

                //Add ambience light
                final float iax = 1;
                final float iay = 1;
                final float iaz = 1;
                lumx = lumx + maaA * iax;
                lumy = lumy + maaA * iay;
                lumz = lumz + maaA * iaz;

                float spetot = 0;

                for(int l = 0; l < lightArr.length; l += lightsLength) {
                    final float ligrox = lightArr[l];
                    final float ligroy = lightArr[l + 1];
                    final float ligroz = lightArr[l + 2];
                    final float ligrdx = lightArr[l + 3];
                    final float ligrdy = lightArr[l + 4];
                    final float ligrdz = lightArr[l + 5];

                    final float ligspex_prenormal = lightArr[l + 6];
                    final float ligspey_prenormal = lightArr[l + 7];
                    final float ligspez_prenormal = lightArr[l + 8];
                    final float ligdiffx_prenormal = lightArr[l + 9];
                    final float ligdiffy_prenormal = lightArr[l + 10];
                    final float ligdiffz_prenormal = lightArr[l + 11];
                    final float ligspenor = length(ligspex_prenormal, ligdiffy_prenormal, ligdiffz_prenormal);
                    final float ligdiffnor = length(ligdiffx_prenormal, ligdiffy_prenormal, ligdiffz_prenormal);
                    final float ligspex = ligspex_prenormal / ligspenor;
                    final float ligspey = ligspey_prenormal / ligspenor;
                    final float ligspez = ligspez_prenormal / ligdiffnor;
                    final float ligdiffx = ligdiffx_prenormal / ligdiffnor;
                    final float ligdiffy = ligdiffy_prenormal / ligdiffnor;
                    final float ligdiffz = ligdiffz_prenormal / ligdiffnor;

                    //-----------Shadows and lights
                    final float uuligx_prenormal = vec3multiplyX(ligrdy, ligrdz, 1.0f);
                    final float uuligy_prenormal = vec3multiplyY(ligrdx, ligrdz, 0.0f, 0.0f);
                    final float uuligz_prenormal = vec3multiplyZ(ligrdx, ligrdy, 0.0f, 1.0f);
                    final float uuligzmod = length(uuligx_prenormal, uuligy_prenormal, uuligz_prenormal);
                    final float uuligx = uuligx_prenormal / uuligzmod;
                    final float uuligy = uuligy_prenormal / uuligzmod;
                    final float uuligz = uuligz_prenormal / uuligzmod;
                    final float vvligx = vec3multiplyX(uuligy, uuligz, ligrdy);
                    final float vvligy = vec3multiplyY(uuligx, uuligz, ligrdx, ligrdz);
                    final float vvligz = vec3multiplyZ(uuligx, uuligy, ligrdx, ligrdy);

                    //Smooth shadow
                    float t = 0.001f;
                    float res = 1.0f;
                    final float blending = 10;
                    for(int s=0; i<25; i++) {
                        final float h = GetDist((posx + 0.001f * norx) + ligrdx*t, (posy + 0.001f * nory) + ligrdy*t, (posz + 0.001f * norz) + ligrdz*t);
                        final float smoothstep = smoothstep(0.0f, 1.0f, blending*h/t);
                        res = Math.min(res, smoothstep);
                        if(res < 0.001f) {
                            i = 25;
                        }
                        t += clamp(h, 0.02f, 2.0f);
                    }
                    final float shadow = clamp(res, 0.0f, 1.0f);
                    final float ligdirx = ligrox - posx;
                    final float ligdiry = ligroy - posy;
                    final float ligdirz = ligroz - posz;
                    final float ligdirmod = length(ligdirx, ligdiry,ligdirz);
                    final float attrib = smoothstep( 0.985f, 0.997f, dot(ligdirx / ligdirmod, ligdiry / ligdirmod, ligdirz / ligdirmod, ligrdx, ligrdy, ligrdz));

                    final float poswwdot = dot(posx, posy, posz, wwx, wwy, wwz);
                    final float ppx = posx - ligrdx*poswwdot;
                    final float ppy = posy - ligrdy*poswwdot;
                    final float ppz = posz - ligrdz*poswwdot;

                    //final float liguvx = dot(ppx, ppy, ppz, uuligx, uuligy, uuligz);
                    final float liguvy = dot(ppx, ppy, ppz, vvligx, vvligy, vvligz);

                    final float pat = smoothstep(-0.5f, 0.5f, (float) Math.sin(10.0 * liguvy));
                    final float sha = pat * attrib * shadow;
                    //-----------End of Shadows and lights

                    //-----------Phong shading
                    final float modlv = length(ligrdx + rdx, ligrdy + rdy, ligrdz + rdz);
                    final float Hx_prenormal = (ligrdx + rdx) / modlv;
                    final float Hy_prenormal = (ligrdy + rdy) / modlv;
                    final float Hz_prenormal = (ligrdz + rdz) / modlv;
                    final float Hmod = length(Hx_prenormal, Hy_prenormal, Hz_prenormal);
                    final float Hx = Hx_prenormal / Hmod;
                    final float Hy = Hy_prenormal / Hmod;
                    final float Hz = Hz_prenormal / Hmod;

                    final float dotligrdnor = dot(ligrdx, ligrdy, ligrdz, norx, nory, norz);
                    final float diffx = maaD * dotligrdnor * ligdiffx * occ * sha;
                    final float diffy = maaD * dotligrdnor * ligdiffy * occ * sha;
                    final float diffz = maaD * dotligrdnor * ligdiffz * occ * sha;

                    if(norx == rdx && nory == rdy && norz == rdz) {
                        maaALPHA *= 4;
                    }
                    final float spedot = (float) Math.pow(dot(norx, nory, norz, Hx, Hy, Hz), maaALPHA);
                    final float spex = maaS * spedot * ligspex * occ;
                    final float spey = maaS * spedot * ligspey * occ;
                    final float spez = maaS * spedot * ligspez * occ;
                    spetot = spetot + spedot;

                    final float fresnel = (float) Math.pow(clamp(1.0f + dot(rdx, rdy, rdz, norx, nory, norz), 0.0f, 1.0f), 3.0f) * occ;

                    lumx += diffx + fresnel * spex;
                    lumy += diffy + fresnel * spey;
                    lumz += diffz + fresnel * spez;
                    //-----------End of Phong shading


                }

                final float linx = vec3multiplyX(maaY, maaZ, lumy);
                final float liny = vec3multiplyY(maaX, maaZ, lumy, lumz);
                final float linz = vec3multiplyZ(maaX, maaY, lumx, lumy);
                final float coltx_pregamma = vec3multiplyX(facy, facz, liny);
                final float colty_pregamma = vec3multiplyY(facx, facz, linx, linz);
                final float coltz_pregamma = vec3multiplyZ(facx, facy, linx, liny);

                //Check to see if it is actually worth doing another reflection cause yk processing power
                //if(coltx > 0.2f && colty > 0.2f && coltz > 0.2f) {
                //Adjust for next reflection pass
                facx = maaX * maaW * spetot;
                facy = maaY * maaW * spetot;
                facz = maaZ * maaW * spetot;
                rqx = posx + norx * 0.01f;
                rqy = posy + nory * 0.01f;
                rqz = posz + norz * 0.01f;
                rdx = refx;
                rdy = refy;
                rdz = refz;
                //}
                //-----------Gamma Correction
                //Temporary gamma correction till i find a better solution
                final float gammacoef = 0.4545f;
                final float coltx = (float) Math.pow( clamp(coltx_pregamma, 0.0f, 1.0f), gammacoef);
                final float colty = (float) Math.pow( clamp(colty_pregamma, 0.0f, 1.0f), gammacoef);
                final float coltz = (float) Math.pow( clamp(coltz_pregamma, 0.0f, 1.0f), gammacoef);

                colx = colx + coltx;
                coly = coly + colty;
                colz = colz + coltz;

                //-----------END OF REFLECTIONS AND MARCHING

            } else {

                colx = 0.52f;
                coly = 0.8f;
                colz = 0.92f;

            }
        }

        //-----------Post Processing TEMPORARY------------

        //-----------Contrast

        final float colx_contrast = colx * 0.6f + 0.4f * vec3multiplyX(coly, colz, coly)*(3.0f - 2.0f*colx);
        final float coly_contrast = coly * 0.6f + 0.4f * vec3multiplyY(colx, colz, colx, colz)*(3.0f - 2.0f*coly);
        final float colz_contrast = colz * 0.6f + 0.4f * vec3multiplyZ(colx, coly, colx, coly)*(3.0f - 2.0f*colz);

        //-----------End of Contrast

        //-----------Saturation
        //Saturation Variable. 0 high sat, 1 low sat
        final float satval = 0.9f;
        final float satoffset = 0.1f; //wtf does this even do tho other than gamma again?

        final float col_contrast_dot = dot(colx_contrast, coly_contrast, colz_contrast, satval, satval, satval);
        final float colx_saturation = mix( colx_contrast,  col_contrast_dot, satoffset);
        final float coly_saturation = mix( coly_contrast,  col_contrast_dot, satoffset);
        final float colz_saturation = mix( colz_contrast,  col_contrast_dot, satoffset);

        //-----------End of Saturation

        //-----------Colour Correction

        //Need to paramaterize these vars
        final float ccx = 0.85f;
        final float ccy = 0.95f;
        final float ccz = 0.9f;

        final float colx_cc = (float) Math.pow(colx_saturation, ccx);
        final float coly_cc = (float) Math.pow(coly_saturation, ccy);
        final float colz_cc = (float) Math.pow(colz_saturation, ccz);

        //-----------End of Colour Correction

        //-----------END OF RENDERING

        //Save to buffer

        final int pxid2 = pxid * 3;

        rgb[pxid2] = colx_cc;
        rgb[pxid2 + 1] = coly_cc;
        rgb[pxid2 + 2] = colz_cc;
        /*
        rgb[pxid2] = pix;
        rgb[pxid2 + 1] = piy;
        rgb[pxid2 + 2] = 0;
        */


    }

    float trace(final float rox, final float roy, final float roz, final float rdx, final float rdy, final float rdz, final float contact) {
        float d = 0;
        for (int i = 0; i < RAYSTEPS; i++) {
            final float px = rox + ( rdx * d );
            final float py = roy + ( rdy * d );
            final float pz = roz + ( rdz * d );

            final float t = GetDist(px, py, pz);
            if(t < (contact) || d > MAX_MARCH) {
                i = RAYSTEPS;
            }
            d = d + t;
        }
        return d;
    }

    float GetDist(final float px, final float py, final float pz) {


        //Generated code
        final float sx = 10;
        final float sy = 3;
        final float sz = 2;
        final float sr = 1;

        float sphereDist =  length(px-sx, py-sy,pz-sz)-sr;
        //End of generated code

        final float dist = py;

        return Math.min(dist, sphereDist);
    }

    /*----------------------------/
    /-----------Texture-----------/
    /----------------------------*/

    void texture(final int px, final int py, final int objID, final float x, final float y) {
        int textureID = objArray[objID + 4];
        if(textureID >= 0) {
            this.texels[px * py] = textures[textureID]; //Red
            this.texels[px * py + 1] = textures[textureID + 1]; //Green
            this.texels[px * py + 2] = textures[textureID + 2]; //Blue
            this.texels[px * py + 3] = textures[textureID + 3]; //Alpha
        } else {
            this.texels[px * py] = 100; //Red
            this.texels[px * py + 1] = 100; //Green
            this.texels[px * py + 2] = 100; //Blue
            this.texels[px * py + 3] = 1; //Alpha
        }
    }


    /*----------------------------/
    /------------Utils------------/
    /----------------------------*/

    float length(final float x, final float y, final float z) {
        return (float) Math.sqrt((Math.pow(x, 2.0f) + Math.pow(y, 2.0f) + Math.pow(z, 2.0f)));
    }

    float vec3multiplyX(final float ay, final float az, final float by) {
        return ay*by - az*by;
    }

    float vec3multiplyY(final float ax, final float az, final float bx, final float bz) {
        return az*bx - ax*bz;
    }

    float vec3multiplyZ(final float ax, final float ay, final float bx, final float by) {
        return ax*by - ay*bx;
    }

    float dot(final float ax, final float ay, final float az, final float bx, final float by, final float bz) {
        return (ax * bx) + (ay * by) + (az * bz);
    }

    float clamp(final float val, final float min, final float max) {
        return Math.max(min, Math.min(max, val));
    }

    float mix(final float x, final float y, final float a) { return x * (1.0f-a) + y * a; }

    float smoothstep(final float a, final float b, final float x) {
        final float t = clamp((x - a) / (b - a), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

}
