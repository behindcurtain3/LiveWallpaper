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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.behindcurtain3.livewallpaper.LiveBar.States;

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
		FloatFFT_1D fft;
		private static final double P0 = 0.000002;
		
		boolean audioEnabled = false;
		AudioRecord recorder;
		public int mSamplesRead; //how many samples read 
		public int buffersizebytes; 
		public int buflen;  
		public short[] buffer; //+-32767  
		private int[] mSampleRates = new int[] { 8000, 11025, 22050, 44100 };
		
		double splValue = 0.0;
		double rmsValue = 0.0;
		
		int offsetX;
		int offsetY;
		int height;
		int width;
		int visibleWidth;

		List<LiveBar> bars = new ArrayList<LiveBar>();
		int barIndex = 0;

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
				fft = new FloatFFT_1D(buflen);
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
				buffer = new short[buffersizebytes];
				mSamplesRead = recorder.read(buffer, 0, buffersizebytes); 
				//Log.d("WALLPAPER", "buffersizebytes: " + buffersizebytes);
				//android.os.Debug.waitForDebugger();
				
				float [] tmpFloats = new float[buffersizebytes];
				
				for (int i = 0; i < buffersizebytes -1 ; i++){
					Short t = buffer[i];
					tmpFloats[i] = t.floatValue();
					//rmsValue += buffer[i] * buffer[i];
				}
				
				fft.complexForward(tmpFloats);
				
				float best_amp = 0.0f;
				float spectrum;
				float freq;
				for (int i = 0; i < buflen; i++){
					spectrum = getSpectrum(tmpFloats, i);
					freq = getFrequency(i);
					
					Log.d("WALLPAPER", "Spectrum: " + spectrum + " Freq: " + freq);
					android.os.Debug.waitForDebugger();
					
					if(spectrum > best_amp){
						best_amp = spectrum;
					}
				}
				
				/*
				double best_amplitude = 0.0;
				int max_freq = Math.round( 600 * buflen / recorder.getSampleRate());
				int min_freq = Math.round( 50 * buflen / recorder.getSampleRate());
				for (int i = min_freq; i <= max_freq; i++){
					
					double current_amplitude = Math.pow(tmpFloats[i * 2], 2) + Math.pow(tmpFloats[i * 2 + 1], 2);
					double normal_amplitude = current_amplitude * Math.pow(50 * 600, 0.5) / current_freq;
					if(normal_amplitude > best_amplitude){
						best_amplitude = normal_amplitude;
					}
				} */
				Log.d("WALLPAPER", "Amplitude: " + best_amp);
				android.os.Debug.waitForDebugger();
				splValue = best_amp;
				/*
				rmsValue = rmsValue / buffersizebytes;
				rmsValue = Math.sqrt(rmsValue);
				
				splValue = 20 * Math.log10(rmsValue / P0);
				splValue += -80; // calibration
				splValue = round(splValue, 2);
				
				Log.d("WALLPAPER", "RMS: " + rmsValue);
				android.os.Debug.waitForDebugger();
				Log.d("WALLPAPER", "SPL: " + splValue);
				android.os.Debug.waitForDebugger(); */
			}
			synchronized (bars) {
				for(int i = 0; i < bars.size(); i++){
					if(bars.get(i).state == States.WAITING && i == barIndex){
						bars.get(i).set((float) splValue);
					}
					bars.get(i).tick();
				}
				iterationCount++;
				
				barIndex++;
				
				if(barIndex >= bars.size()){
					barIndex = 0;
				}
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
		
		public double round(double d, int decimalPlace){
			BigDecimal bd = new BigDecimal(Double.toString(d));
			bd = bd.setScale(decimalPlace, BigDecimal.ROUND_HALF_UP);
			return bd.doubleValue();
		}
		
		public float getSpectrum(float[] a, int index){
			return (float) (Math.sqrt( Math.pow(a[index * 2], 2) + Math.pow(a[index * 2 + 1], 2)));
		}
		
		public float getFrequency(int index){
			return (float) (((1.0 * recorder.getSampleRate()) / ( 1.0 * buflen)) * index);
		}

	}

}
