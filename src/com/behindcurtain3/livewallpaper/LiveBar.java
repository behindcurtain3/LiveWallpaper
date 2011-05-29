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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

public class LiveBar {
	public enum States { UP, DOWN, PAUSED, WAITING }
	
	RectF rect;
	int height;
	int maxHeight;
	int xoffset;
	
	int color;
	int alpha;

	float level;
	float originLevel;
	
	int steps;
	int currentStep;
	int pause;
	int pausedStep;
	
	States state;
	

	Bitmap bitmap;

	public LiveBar(RectF rect, int height, int offset, int steps) {
		this.rect = rect;
		this.height = height;
		this.xoffset = offset;
		this.maxHeight = (int) (height * 0.8);
		

		this.color = 100;
		this.alpha = 0;

		this.level = 10;
		this.originLevel = this.level;
		this.steps = steps;
		this.currentStep = 0;
		this.pause = 3;
		
		this.state = States.WAITING;
	}
	
	void set(float level){
		this.currentStep = this.steps;
		this.level = level;
		this.state = States.PAUSED;
		this.pausedStep = 0;
	}

	void tick() {	
		
		float fraction;
		
		switch(this.state){
		case UP:
			this.currentStep++;			
			
			if(this.currentStep >= this.steps){
				this.state = States.PAUSED;
				this.pausedStep = 0;
			}
			break;
		case DOWN:
			if(this.currentStep > 0)
				this.currentStep--;
			else
				this.state = States.WAITING;
			
			break;
		case PAUSED:
			this.pausedStep++;
			
			if(this.pausedStep > this.pause){
				this.state = States.DOWN;
			}
			break;
		}
		
		// Update the bar level
		fraction = (float) this.currentStep / (float) this.steps;
		float drawAt = this.level * fraction;
		this.rect.top = this.rect.bottom - this.originLevel - drawAt;
		
		if (fraction <= 0.25f) {
			this.alpha = (int) (128 * 4.0f * fraction);
		} else {
			this.alpha = (int) (-128 * (fraction - 1) / 0.75f);
		}
		
		if(this.alpha < 50){
			this.alpha = 50;
		}
		
		// Update color
		float yFraction = (float) rect.top / (float) this.height;
		yFraction = yFraction + 0.05f - (float) (0.1f * (Math.random()));
		if (yFraction < 0.0f)
			yFraction += 1.0f;
		if (yFraction > 1.0f)
			yFraction -= 1.0f;
		this.color = getColor(yFraction);
	}
	
	int getColor(float yFraction) {
		return Color.HSVToColor(this.alpha, new float[] { 360.0f * yFraction, 1.0f,	1.0f });
	}

}
