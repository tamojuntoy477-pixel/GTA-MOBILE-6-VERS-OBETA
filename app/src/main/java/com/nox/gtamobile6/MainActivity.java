package com.nox.gtamobile6;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;
import android.view.View;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends Activity {
    private GameView game;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
             View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
             View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        game = new GameView(this);
        setContentView(game);
    }
    @Override protected void onPause() { super.onPause(); if (game != null) game.onPause(); }
    @Override protected void onResume() { super.onResume(); if (game != null) game.onResume(); }

    static class GameView extends GLSurfaceView {
        final CityRenderer renderer;
        float downX, downY;
        GameView(Activity a) {
            super(a);
            setEGLContextClientVersion(2);
            setEGLConfigChooser(8,8,8,8,16,0);
            renderer = new CityRenderer();
            setRenderer(renderer);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = e.getX(); downY = e.getY(); return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                final float nx = (e.getX() / Math.max(1f, getWidth()) - .5f) * 22f;
                final float nz = (e.getY() / Math.max(1f, getHeight()) - .5f) * 22f;
                queueEvent(() -> renderer.setTarget(nx, -nz));
                return true;
            }
            return true;
        }
    }

    static class CityRenderer implements GLSurfaceView.Renderer {
        private static final String VS =
            "uniform mat4 uMVP; uniform mat4 uModel; attribute vec3 aPosition; attribute vec3 aNormal; varying vec3 vNormal; varying float vDepth; void main(){ vec4 p=uMVP*vec4(aPosition,1.0); gl_Position=p; vNormal=normalize(mat3(uModel)*aNormal); vDepth=abs(p.z/p.w); }";
        private static final String FS =
            "precision mediump float; uniform vec4 uColor; varying vec3 vNormal; varying float vDepth; void main(){ vec3 n=normalize(vNormal); vec3 light=normalize(vec3(-0.42,0.82,0.36)); float diffuse=max(dot(n,light),0.0); float hemi=0.30+0.22*max(n.y,0.0); float shade=clamp(hemi+diffuse*0.62,0.28,1.0); vec3 lit=uColor.rgb*shade; float fog= smoothstep(0.72,0.995,vDepth); vec3 sky=vec3(0.53,0.70,0.82); gl_FragColor=vec4(mix(lit,sky,fog*0.58),uColor.a); }";
        private final float[] cube = {
            -1,-1,1, 1,-1,1, 1,1,1, -1,-1,1, 1,1,1, -1,1,1,
            1,-1,-1, -1,-1,-1, -1,1,-1, 1,-1,-1, -1,1,-1, 1,1,-1,
            -1,-1,-1, -1,-1,1, -1,1,1, -1,-1,-1, -1,1,1, -1,1,-1,
            1,-1,1, 1,-1,-1, 1,1,-1, 1,-1,1, 1,1,-1, 1,1,1,
            -1,1,1, 1,1,1, 1,1,-1, -1,1,1, 1,1,-1, -1,1,-1,
            -1,-1,-1, 1,-1,-1, 1,-1,1, -1,-1,-1, 1,-1,1, -1,-1,1
        };
        private FloatBuffer vertices, sphereVertices, cubeNormals;
        private int sphereVertexCount;
        private int program, positionHandle, normalHandle, mvpHandle, modelHandle, colorHandle;
        private final float[] projection = new float[16], view = new float[16];
        private final float[] model = new float[16], mvp = new float[16], temp = new float[16];
        private volatile float playerX = 0, playerZ = 0, targetX = 0, targetZ = 0;
        private int width = 1, height = 1;

        void setTarget(float x, float z) {
            targetX = Math.max(-18, Math.min(18, x));
            targetZ = Math.max(-18, Math.min(18, z));
        }
        @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0.48f, 0.70f, 0.88f, 1f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            ByteBuffer bb = ByteBuffer.allocateDirect(cube.length * 4).order(ByteOrder.nativeOrder());
            vertices = bb.asFloatBuffer(); vertices.put(cube).position(0);
            float[] faceNormals = new float[108];
            float[][] dirs = {{0,0,1},{0,0,-1},{-1,0,0},{1,0,0},{0,1,0},{0,-1,0}};
            for (int face=0; face<6; face++) for (int vertex=0; vertex<6; vertex++) {
                int k=(face*6+vertex)*3;
                faceNormals[k]=dirs[face][0]; faceNormals[k+1]=dirs[face][1]; faceNormals[k+2]=dirs[face][2];
            }
            ByteBuffer nb=ByteBuffer.allocateDirect(faceNormals.length*4).order(ByteOrder.nativeOrder());
            cubeNormals=nb.asFloatBuffer(); cubeNormals.put(faceNormals).position(0);
            sphereVertices = buildSphere(24, 32);
            sphereVertexCount = sphereVertices.limit() / 3;
            int v = shader(GLES20.GL_VERTEX_SHADER, VS);
            int f = shader(GLES20.GL_FRAGMENT_SHADER, FS);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, v); GLES20.glAttachShader(program, f);
            GLES20.glLinkProgram(program);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            normalHandle = GLES20.glGetAttribLocation(program, "aNormal");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");
            modelHandle = GLES20.glGetUniformLocation(program, "uModel");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
        }
        private FloatBuffer buildSphere(int stacks, int slices) {
            java.util.ArrayList<Float> data = new java.util.ArrayList<>();
            for (int i=0;i<stacks;i++) {
                double a0=Math.PI*i/stacks, a1=Math.PI*(i+1)/stacks;
                for (int j=0;j<slices;j++) {
                    double b0=2*Math.PI*j/slices, b1=2*Math.PI*(j+1)/slices;
                    addTriangle(data,a0,b0,a1,b0,a1,b1);
                    addTriangle(data,a0,b0,a1,b1,a0,b1);
                }
            }
            float[] out=new float[data.size()];
            for(int i=0;i<out.length;i++) out[i]=data.get(i);
            ByteBuffer bytes=ByteBuffer.allocateDirect(out.length*4).order(ByteOrder.nativeOrder());
            FloatBuffer result=bytes.asFloatBuffer(); result.put(out).position(0); return result;
        }
        private void addTriangle(java.util.ArrayList<Float> out,double a,double b,double c,double d,double e,double f) {
            addPoint(out,a,b); addPoint(out,c,d); addPoint(out,e,f);
        }
        private void addPoint(java.util.ArrayList<Float> out,double a,double b) {
            out.add((float)(Math.sin(a)*Math.cos(b)));
            out.add((float)Math.cos(a));
            out.add((float)(Math.sin(a)*Math.sin(b)));
        }
        private void sphere(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(temp,0,view,0,model,0);
            Matrix.multiplyMM(mvp,0,projection,0,temp,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            sphereVertices.position(0);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,sphereVertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sphereVertexCount);
        }
        private int shader(int type, String source) {
            int s = GLES20.glCreateShader(type);
            GLES20.glShaderSource(s, source); GLES20.glCompileShader(s); return s;
        }
        @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
            width = Math.max(1,w); height = Math.max(1,h);
            GLES20.glViewport(0,0,width,height);
            Matrix.perspectiveM(projection,0,55f,(float)width/height,0.1f,120f);
        }
        @Override public void onDrawFrame(GL10 gl) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            playerX += (targetX-playerX)*0.045f;
            playerZ += (targetZ-playerZ)*0.045f;
            Matrix.setLookAtM(view,0,playerX+8f,8.5f,playerZ+11f,playerX,0f,playerZ,0f,1f,0f);
            GLES20.glUseProgram(program);
            vertices.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glEnableVertexAttribArray(normalHandle);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,cubeNormals);

            box(0,-0.28f,0,76f,0.25f,76f,0.22f,0.42f,0.25f); // expanded city terrain
            // Asphalt avenues and sidewalks
            box(0,-0.12f,0,5.2f,0.04f,48f,0.16f,0.17f,0.19f);
            box(0,-0.10f,0,48f,0.04f,5.2f,0.16f,0.17f,0.19f);
            box(0,-0.07f,0,0.18f,0.025f,40f,0.85f,0.72f,0.35f);
            box(0,-0.07f,0,40f,0.025f,0.18f,0.85f,0.72f,0.35f);
            // Lane markings and crosswalks
            for (int i=-4;i<=4;i++) {
                box(i*4.2f,-0.045f,0,0.055f,0.018f,2.0f,0.92f,0.88f,0.66f);
                box(0,-0.045f,i*4.2f,2.0f,0.018f,0.055f,0.92f,0.88f,0.66f);
            }
            // Expanded urban grid: varied towers, apartments and commercial blocks
            for (int x=-4;x<=4;x++) for (int z=-4;z<=4;z++) {
                if (Math.abs(x)<=1 || Math.abs(z)<=1) continue;
                float bx=x*8.4f, bz=z*8.4f;
                float bh=2.8f + ((x*x+z*z+x*3+z*5)&3)*1.15f;
                float bw=1.8f+((x+z+12)&1)*0.55f;
                float r=0.30f + ((x+z+16)&3)*0.045f;
                box(bx,bh*0.5f,bz,bw,bh*0.5f,2.35f,r,0.37f,0.40f);
                box(bx,bh+0.14f,bz,0.72f,0.14f,0.72f,0.18f,0.20f,0.23f);
                for (int floor=0; floor<Math.max(1,(int)(bh/1.0f)); floor++) {
                    float wy=0.45f+floor*0.92f;
                    box(bx-bw*0.48f,wy,bz-2.36f,0.32f,0.23f,0.025f,0.28f,0.66f,0.82f);
                    box(bx+bw*0.48f,wy,bz-2.36f,0.32f,0.23f,0.025f,0.28f,0.66f,0.82f);
                }
            }
            // Secondary boulevards create distinct city districts
            for (int road=-2;road<=2;road++) {
                if (road==0) continue;
                float lane=road*14.5f;
                box(lane,-0.115f,0,2.5f,0.045f,70f,0.18f,0.19f,0.21f);
                box(0,-0.115f,lane,70f,0.045f,2.5f,0.18f,0.19f,0.21f);
                for(int mark=-6;mark<=6;mark++) {
                    box(lane,-0.055f,mark*5.0f,0.045f,0.012f,1.6f,0.88f,0.82f,0.64f);
                    box(mark*5.0f,-0.055f,lane,1.6f,0.012f,0.045f,0.88f,0.82f,0.64f);
                }
            }
            // Trees around the blocks
            for (int i=-4;i<=4;i++) {
                tree(i*8.4f+2.1f, -5.2f);
                tree(i*8.4f-2.1f, 5.2f);
            }
            // Street furniture: lamps and bus-stop shelters
            for (int i=-3;i<=3;i++) {
                streetLamp(i*8.4f+3.0f,-6.0f);
                streetLamp(i*8.4f-3.0f,6.0f);
            }
            // Small park plaza with paths and benches
            box(10f,-0.015f,10f,3.2f,0.035f,3.2f,0.28f,0.48f,0.25f);
            box(10f,0.03f,10f,0.18f,0.025f,2.7f,0.72f,0.68f,0.55f);
            box(10f,0.03f,10f,2.7f,0.025f,0.18f,0.72f,0.68f,0.55f);
            bench(8.2f,9.0f);
            bench(11.8f,11.0f);
            // Moving traffic: lightweight original low-poly cars
            trafficCar(-3.9f, 0.28f, -8f, 0.92f, 0.22f, 0.42f);
            trafficCar(3.9f, 0.28f, 7f, 0.18f, 0.38f, 0.82f);
            trafficCar(-8f, 0.28f, 3.9f, 0.82f, 0.68f, 0.12f);
            trafficCar(8f, 0.28f, -3.9f, 0.20f, 0.68f, 0.38f);
            // Detailed original player avatar: jacket, shirt, trousers, shoes, hair and face
            drawPlayer(playerX,playerZ);
            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(normalHandle);
        }
        private void trafficCar(float x,float y,float z,float r,float g,float b) {
            box(x,y,z,0.62f,0.24f,1.05f,r,g,b);
            box(x,y+0.27f,z-0.10f,0.40f,0.19f,0.48f,0.16f,0.22f,0.28f);
            box(x-0.42f,y-0.12f,z-0.66f,0.16f,0.16f,0.22f,0.06f,0.06f,0.07f);
            box(x+0.42f,y-0.12f,z-0.66f,0.16f,0.16f,0.22f,0.06f,0.06f,0.07f);
            box(x-0.42f,y-0.12f,z+0.66f,0.16f,0.16f,0.22f,0.06f,0.06f,0.07f);
            box(x+0.42f,y-0.12f,z+0.66f,0.16f,0.16f,0.22f,0.06f,0.06f,0.07f);
        }
        private void drawPlayer(float x,float z) {
            // Original rounded humanoid avatar. Front faces -Z.
            float phase = (float)(System.nanoTime()*0.0000000035);
            float stride = (float)Math.sin(phase);
            float opposite = (float)Math.sin(phase + Math.PI);
            float moving = Math.min(1f, (float)(Math.hypot(targetX-playerX,targetZ-playerZ)*0.35));
            float bob = Math.abs(stride)*0.035f*moving;
            float armSwing = stride*0.16f*moving;
            float legSwing = stride*0.13f*moving;
            float skinR=0.58f, skinG=0.34f, skinB=0.22f;
            float jacketR=0.075f, jacketG=0.19f, jacketB=0.52f;
            float pantsR=0.105f, pantsG=0.13f, pantsB=0.19f;

            // Shoes, legs and knees. Ellipsoids keep the silhouette rounded.
            sphere(x-0.145f,0.105f+bob,z-0.075f+legSwing*0.25f,0.15f,0.09f,0.245f,0.035f,0.04f,0.055f);
            sphere(x+0.145f,0.105f+bob,z-0.075f-legSwing*0.25f,0.15f,0.09f,0.245f,0.035f,0.04f,0.055f);
            sphere(x-0.145f,0.34f+bob,z+legSwing*0.25f,0.095f,0.235f,0.105f,0.16f,0.18f,0.22f);
            sphere(x+0.145f,0.34f+bob,z-legSwing*0.25f,0.095f,0.235f,0.105f,0.16f,0.18f,0.22f);
            sphere(x-0.145f,0.66f+bob+legSwing,z,0.125f,0.245f,0.14f,pantsR,pantsG,pantsB);
            sphere(x+0.145f,0.66f+bob-legSwing,z,0.125f,0.245f,0.14f,pantsR,pantsG,pantsB);
            sphere(x,0.91f+bob,z,0.255f,0.19f,0.16f,0.09f,0.15f,0.36f);

            // Torso, chest, jacket panels and collar.
            sphere(x,1.16f+bob,z,0.315f,0.33f,0.19f,jacketR,jacketG,jacketB);
            sphere(x,1.36f+bob,z+0.005f,0.335f,0.155f,0.19f,0.10f,0.25f,0.62f);
            sphere(x-0.105f,1.19f+bob,z-0.181f,0.018f,0.24f,0.018f,0.20f,0.30f,0.58f);
            sphere(x+0.105f,1.19f+bob,z-0.181f,0.018f,0.24f,0.018f,0.20f,0.30f,0.58f);
            sphere(x,1.46f+bob,z-0.005f,0.115f,0.105f,0.12f,0.82f,0.80f,0.72f);
            sphere(x,1.49f+bob,z+0.012f,0.105f,0.07f,0.105f,skinR,skinG,skinB);

            // Shoulders, upper arms, elbows, forearms and hands.
            sphere(x-0.335f,1.34f+bob,z,0.155f,0.17f,0.155f,jacketR,jacketG,jacketB);
            sphere(x+0.335f,1.34f+bob,z,0.155f,0.17f,0.155f,jacketR,jacketG,jacketB);
            sphere(x-0.405f,1.19f+bob,z-armSwing*0.35f,0.115f,0.19f,0.115f,0.085f,0.21f,0.56f);
            sphere(x+0.405f,1.19f+bob,z+armSwing*0.35f,0.115f,0.19f,0.115f,0.085f,0.21f,0.56f);
            sphere(x-0.435f,0.99f+bob,z-armSwing,0.09f,0.17f,0.095f,skinR,skinG,skinB);
            sphere(x+0.435f,0.99f+bob,z+armSwing,0.09f,0.17f,0.095f,skinR,skinG,skinB);
            sphere(x-0.445f,0.82f+bob,z-armSwing,0.085f,0.085f,0.085f,skinR,skinG,skinB);
            sphere(x+0.445f,0.82f+bob,z+armSwing,0.085f,0.085f,0.085f,skinR,skinG,skinB);

            // Neck, head, ears and a defined face.
            sphere(x,1.55f+bob,z,0.09f,0.13f,0.09f,skinR,skinG,skinB);
            sphere(x,1.78f+bob,z,0.185f,0.245f,0.175f,skinR,skinG,skinB);
            sphere(x-0.184f,1.77f+bob,z,0.045f,0.07f,0.045f,skinR,skinG,skinB);
            sphere(x+0.184f,1.77f+bob,z,0.045f,0.07f,0.045f,skinR,skinG,skinB);
            // Hair cap and side hair.
            sphere(x,1.965f+bob,z+0.005f,0.19f,0.085f,0.18f,0.035f,0.025f,0.02f);
            sphere(x-0.15f,1.87f+bob,z+0.015f,0.055f,0.13f,0.15f,0.035f,0.025f,0.02f);
            sphere(x+0.15f,1.87f+bob,z+0.015f,0.055f,0.13f,0.15f,0.035f,0.025f,0.02f);
            // Eyes, brows, nose and mouth on the front plane.
            sphere(x-0.075f,1.805f+bob,z-0.157f,0.022f,0.018f,0.014f,0.025f,0.02f,0.018f);
            sphere(x+0.075f,1.805f+bob,z-0.157f,0.022f,0.018f,0.014f,0.025f,0.02f,0.018f);
            sphere(x-0.075f,1.845f+bob,z-0.155f,0.045f,0.012f,0.018f,0.045f,0.03f,0.025f);
            sphere(x+0.075f,1.845f+bob,z-0.155f,0.045f,0.012f,0.018f,0.045f,0.03f,0.025f);
            sphere(x,1.735f+bob,z-0.181f,0.027f,0.045f,0.028f,0.48f,0.25f,0.16f);
            sphere(x,1.675f+bob,z-0.165f,0.043f,0.009f,0.012f,0.20f,0.045f,0.04f);
            // Backpack and seams.
            sphere(x,1.13f+bob,z+0.205f,0.19f,0.25f,0.075f,0.045f,0.055f,0.075f);
            sphere(x-0.24f,1.12f+bob,z+0.08f,0.035f,0.19f,0.045f,0.055f,0.10f,0.28f);
            sphere(x+0.24f,1.12f+bob,z+0.08f,0.035f,0.19f,0.045f,0.055f,0.10f,0.28f);
        }
        private void streetLamp(float x,float z) {
            box(x,1.45f,z,0.055f,1.45f,0.055f,0.20f,0.21f,0.23f);
            box(x,2.92f,z,0.22f,0.10f,0.22f,0.95f,0.84f,0.48f);
            box(x+0.28f,2.88f,z,0.32f,0.045f,0.045f,0.20f,0.21f,0.23f);
        }
        private void bench(float x,float z) {
            box(x,0.35f,z,0.72f,0.08f,0.22f,0.45f,0.25f,0.12f);
            box(x,0.58f,z+0.17f,0.72f,0.28f,0.08f,0.45f,0.25f,0.12f);
            box(x-0.5f,0.16f,z,0.06f,0.20f,0.07f,0.18f,0.18f,0.18f);
            box(x+0.5f,0.16f,z,0.06f,0.20f,0.07f,0.18f,0.18f,0.18f);
        }
        private void tree(float x,float z) {
            box(x,0.65f,z,0.16f,0.65f,0.16f,0.34f,0.20f,0.10f);
            box(x,1.65f,z,0.72f,0.85f,0.72f,0.10f,0.38f,0.16f);
        }
        private void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b) {
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(temp,0,view,0,model,0);
            Matrix.multiplyMM(mvp,0,projection,0,temp,0);
            GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0);
            GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0);
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            vertices.position(0); cubeNormals.position(0);
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices);
            GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,12,cubeNormals);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
