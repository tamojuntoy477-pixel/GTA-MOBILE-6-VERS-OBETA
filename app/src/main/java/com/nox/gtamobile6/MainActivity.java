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
            "uniform mat4 uMVP; attribute vec3 aPosition; void main(){ gl_Position=uMVP*vec4(aPosition,1.0); }";
        private static final String FS =
            "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }";
        private final float[] cube = {
            -1,-1,1, 1,-1,1, 1,1,1, -1,-1,1, 1,1,1, -1,1,1,
            1,-1,-1, -1,-1,-1, -1,1,-1, 1,-1,-1, -1,1,-1, 1,1,-1,
            -1,-1,-1, -1,-1,1, -1,1,1, -1,-1,-1, -1,1,1, -1,1,-1,
            1,-1,1, 1,-1,-1, 1,1,-1, 1,-1,1, 1,1,-1, 1,1,1,
            -1,1,1, 1,1,1, 1,1,-1, -1,1,1, 1,1,-1, -1,1,-1,
            -1,-1,-1, 1,-1,-1, 1,-1,1, -1,-1,-1, 1,-1,1, -1,-1,1
        };
        private FloatBuffer vertices;
        private int program, positionHandle, mvpHandle, colorHandle;
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
            int v = shader(GLES20.GL_VERTEX_SHADER, VS);
            int f = shader(GLES20.GL_FRAGMENT_SHADER, FS);
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, v); GLES20.glAttachShader(program, f);
            GLES20.glLinkProgram(program);
            positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            mvpHandle = GLES20.glGetUniformLocation(program, "uMVP");
            colorHandle = GLES20.glGetUniformLocation(program, "uColor");
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
            GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,12,vertices);

            box(0,-0.28f,0,48f,0.25f,48f,0.22f,0.42f,0.25f); // terrain
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
            // Original low-poly city blocks
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)<=1 || Math.abs(z)<=1) continue;
                float bx=x*7.2f, bz=z*7.2f;
                float bh=2.0f + ((x*x+z*z)%4)*0.8f;
                float r=0.34f + ((x+z+8)%3)*0.07f;
                box(bx,bh*0.5f,bz,2.2f,bh*0.5f,2.2f,r,0.38f,0.42f);
                // rooftop detail and illuminated-looking window panels
                box(bx,bh+0.12f,bz,0.65f,0.12f,0.65f,0.25f,0.28f,0.32f);
                for (int floor=0; floor<Math.max(1,(int)(bh/1.15f)); floor++) {
                    float wy=0.35f+floor*1.0f;
                    box(bx-0.72f,wy,bz-2.205f,0.34f,0.25f,0.025f,0.30f,0.68f,0.82f);
                    box(bx+0.72f,wy,bz-2.205f,0.34f,0.25f,0.025f,0.30f,0.68f,0.82f);
                }
            }
            // Trees around the blocks
            for (int i=-3;i<=3;i++) {
                tree(i*7.2f+2.1f, -3.7f);
                tree(i*7.2f-2.1f, 3.7f);
            }
            // Street furniture: lamps and bus-stop shelters
            for (int i=-3;i<=3;i++) {
                streetLamp(i*7.2f+3.0f,-5.0f);
                streetLamp(i*7.2f-3.0f,5.0f);
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
            float bob = (float)Math.sin(System.nanoTime()*0.000000006)*0.035f;
            box(x,0.82f+bob,z,0.34f,0.48f,0.23f,0.10f,0.24f,0.72f); // blue jacket
            box(x,0.84f+bob,z-0.235f,0.15f,0.30f,0.025f,0.90f,0.90f,0.86f); // shirt
            box(x-0.43f,0.82f+bob,z,0.12f,0.38f,0.13f,0.10f,0.24f,0.72f);
            box(x+0.43f,0.82f+bob,z,0.12f,0.38f,0.13f,0.10f,0.24f,0.72f);
            box(x-0.43f,0.55f+bob,z,0.11f,0.12f,0.12f,0.72f,0.48f,0.32f);
            box(x+0.43f,0.55f+bob,z,0.11f,0.12f,0.12f,0.72f,0.48f,0.32f);
            box(x-0.17f,0.30f+bob,z,0.14f,0.30f,0.15f,0.12f,0.15f,0.22f);
            box(x+0.17f,0.30f+bob,z,0.14f,0.30f,0.15f,0.12f,0.15f,0.22f);
            box(x-0.17f,0.055f+bob,z-0.04f,0.17f,0.09f,0.25f,0.045f,0.05f,0.06f);
            box(x+0.17f,0.055f+bob,z-0.04f,0.17f,0.09f,0.25f,0.045f,0.05f,0.06f);
            box(x,1.48f+bob,z,0.25f,0.29f,0.23f,0.72f,0.48f,0.32f); // head
            box(x,1.69f+bob,z,0.26f,0.12f,0.25f,0.08f,0.055f,0.04f); // hair
            box(x-0.09f,1.50f+bob,z-0.205f,0.035f,0.035f,0.025f,0.04f,0.04f,0.04f);
            box(x+0.09f,1.50f+bob,z-0.205f,0.035f,0.035f,0.025f,0.04f,0.04f,0.04f);
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
            GLES20.glUniform4f(colorHandle,r,g,b,1f);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
