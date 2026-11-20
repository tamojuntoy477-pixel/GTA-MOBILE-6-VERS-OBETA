package com.nox.gtamobile6;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.MotionEvent;
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
            // Original low-poly city blocks
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) {
                if (Math.abs(x)<=1 || Math.abs(z)<=1) continue;
                float bx=x*7.2f, bz=z*7.2f;
                float bh=2.0f + ((x*x+z*z)%4)*0.8f;
                float r=0.34f + ((x+z+8)%3)*0.07f;
                box(bx,bh*0.5f,bz,2.2f,bh*0.5f,2.2f,r,0.38f,0.42f);
                // rooftop detail
                box(bx,bh+0.12f,bz,0.65f,0.12f,0.65f,0.25f,0.28f,0.32f);
            }
            // Trees around the blocks
            for (int i=-3;i<=3;i++) {
                tree(i*7.2f+2.1f, -3.7f);
                tree(i*7.2f-2.1f, 3.7f);
            }
            // Player character placeholder, fully 3D
            box(playerX,0.72f,playerZ,0.38f,0.72f,0.30f,0.82f,0.12f,0.10f);
            box(playerX,1.62f,playerZ,0.29f,0.25f,0.29f,0.88f,0.68f,0.52f);
            GLES20.glDisableVertexAttribArray(positionHandle);
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
