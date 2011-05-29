/*
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.behindcurtain3.livewallpaper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaRecorder.AudioSource;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;

public class DecibelWallpaper extends AnimationWallpaper {

	@Override
	public Engine onCreateEngine() {
		android.os.Debug.waitForDebugger();
		
		return new LiveEngine();
	}

	class LiveEngine extends AnimationEngine {
		boolean audioEnabled = false;
		AudioRecord recorder;
		public int mSamplesRead; //how many samples read 
		public int buffersizebytes; 
		public int buflen; 
		public int channelConfiguration = AudioFormat.CHANNEL_CONFIGURATION_MONO;
		public int audioEncoding = AudioFormat.ENCODING_PCM_16BIT; 
		public short[] buffer; //+-32767 
		public static final int SAMPPERSEC = 8000; //samp per sec 8000, 11025, 22050 44100 or 48000 
		private int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100 };
		
		int offsetX;
		int offsetY;
		int height;
		int width;
		int visibleWidth;

		Set<LiveBar> bars = new HashSet<LiveBar>();

		int iterationCount = 0;

		Paint paint = new Paint();

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {			
			super.onCreate(surfaceHolder);

			// By default we don't get touch events, so enable them.
			setTouchEventsEnabled(true);
			
			recorder = findAudioRecord();
			
			if(recorder != null){
				buffer = new short[buffersizebytes];
				buflen = buffersizebytes / 2;
				audioEnabled = true;
			}
			
		}
		
		@Override
		public void onDestroy() {
			super.onDestroy();
			
			if(isAudio()){
				recorder.release();
				audioEnabled = false;
			}

		}
		
		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			
			if(isAudio()){
			
				if(visible){
					try{
						recorder.startRecording();
					} catch(IllegalStateException e){
						Log.e("WALLPAPER", e.getMessage());
					}
				} else {
					try {
						recorder.stop();
					} catch(IllegalStateException e){
						Log.e("WALLPAPER", e.getMessage());
					}
				}
			}
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format,
				int width, int height) {

			this.height = height;
			if (this.isPreview()) {
				this.width = width;
			} else {
				this.width = 2 * width;
			}
			this.visibleWidth = width;

			synchronized(this.bars){
				this.bars.clear();
			}			
			generateBars();

			super.onSurfaceChanged(holder, format, width, height);
		}

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset,
				float xOffsetStep, float yOffsetStep, int xPixelOffset,
				int yPixelOffset) {
			// store the offsets
			this.offsetX = xPixelOffset;
			this.offsetY = yPixelOffset;

			super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep,
					xPixelOffset, yPixelOffset);
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z,
				Bundle extras, boolean resultRequested) {
			if ("android.wallpaper.tap".equals(action)) {
				createBar(x - this.offsetX, y - this.offsetY);
			}
			return super.onCommand(action, x, y, z, extras, resultRequested);
		}

		@Override
		protected void drawFrame() {
			SurfaceHolder holder = getSurfaceHolder();

			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null) {
					draw(c);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		void draw(Canvas c) {
			c.save();
			c.drawColor(0xff000000);

			synchronized (bars) {
				for (LiveBar bar : bars) {
					if (bar.alpha == 0)
						continue;

					// intersects with the screen?
					float minX = bar.rect.right;
					if (minX > (-this.offsetX + this.visibleWidth)) {
						continue;
					}
					float maxX = bar.rect.left;
					if (maxX < -this.offsetX) {
						continue;
					}

					paint.setAntiAlias(true);

					// paint the fill
					paint.setColor(Color.argb(bar.alpha, Color.red(bar.color), Color.green(bar.color),	Color.blue(bar.color)));
					paint.setStyle(Paint.Style.FILL_AND_STROKE);
					c.drawRect(bar.rect.left + this.offsetX, bar.rect.top + this.offsetY, bar.rect.right + this.offsetX, bar.rect.bottom + this.offsetY, paint);

					// paint the contour
					paint.setColor(Color.argb(bar.alpha, 63 + 3 * Color.red(bar.color) / 4, 63 + 3 * Color.green(bar.color) / 4, 63 + 3 * Color.blue(bar.color) / 4));
					paint.setStyle(Paint.Style.STROKE);
					paint.setStrokeWidth(3.0f);
					c.drawRect(bar.rect.left + this.offsetX, bar.rect.top + this.offsetY, bar.rect.right + this.offsetX, bar.rect.bottom + this.offsetY, paint);
				}
			}

			c.restore();
		}

		@Override
		protected void iteration() {	
			if(isAudio()){
				if(recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED){
					recorder.startRecording();
				}
				mSamplesRead = recorder.read(buffer, 0, buffersizebytes); 
				Log.d("WALLPAPER", "buffersizebytes: " + buffersizebytes);
				android.os.Debug.waitForDebugger();
				for (int i = 0; i < 256; i++){
					Log.d("WALLPAPER-BUFFER", i + ": " +buffer[i]);
					android.os.Debug.waitForDebugger();
				}
			}
			synchronized (bars) {
				for (Iterator<LiveBar> it = bars.iterator(); it
						.hasNext();) {
					LiveBar bar = it.next();
					bar.tick();
				}
				iterationCount++;
			}

			super.iteration();
		}

		void createRandomBar() {
			int x = (int) (width * Math.random());
			int y = (int) (height * Math.random());
			createBar(x, y);
		}

		void createBar(int x, int width) {
			int offset = (int) (this.height * 0.05);
			RectF rect = new RectF();
			rect.left = x;
			rect.bottom = height - offset;
			rect.right = rect.left + width;
			rect.top = rect.bottom - 10;
			
			
			//int steps = 10 + (int) (10 * Math.random());
			LiveBar bar = new LiveBar( rect, height, offset, 20 );
			synchronized (this.bars) {
				this.bars.add(bar);
			}
		}
		
		void generateBars(){
			int x = 5;
			int width = 25;
			int every = 40;
			
			do {
				createBar(x, width);
				x += every;
			} while ((x + width) < this.width);
		}
		
		public AudioRecord findAudioRecord() {
		    for (int rate : mSampleRates) {
		        for (short audioFormat : new short[] { AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT }) {
		            for (short channelConfig : new short[] { AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO }) {
		                try {
		                    Log.d("WALLPAPER", "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
		                            + channelConfig);
		                    int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

		                    if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
		                        // check if we can instantiate and have a success
		                        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, rate, channelConfig, audioFormat, bufferSize);

		                        if (recorder.getState() == AudioRecord.STATE_INITIALIZED){
		                        	this.buffersizebytes = bufferSize;
		                        	return recorder;
		                        }
		                    }
		                } catch (Exception e) {
		                    Log.e("WALLPAPER", rate + "Exception, keep trying.",e);
		                }
		            }
		        }
		    }
		    return null;
		}
		
		public boolean isAudio(){
			return audioEnabled;
		}

	}

}
