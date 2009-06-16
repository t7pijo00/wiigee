/*
 * wiigee - accelerometerbased gesture recognition
 * Copyright (C) 2007, 2008 Benjamin Poppinga
 * 
 * Developed at University of Oldenburg
 * Contact: benjamin.poppinga@informatik.uni-oldenburg.de
 *
 * This file is part of wiigee.
 *
 * wiigee is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.wiigee.device;

import java.io.IOException;
import java.util.EventObject;

import javax.bluetooth.L2CAPConnection;

import org.wiigee.event.ButtonPressedEvent;
import org.wiigee.event.ButtonReleasedEvent;
import org.wiigee.util.Log;

/**
 * This class listens to data sended by the wiimote and generates specific
 * events for acceleration, buttonpress, ...
 * 
 * @author Benjamin 'BePo' Poppinga
 * 
 */
public class WiimoteStreamer extends Thread {

	boolean running;
	double x0, x1, y0, y1, z0, z1;

	Wiimote wiimote;

	private L2CAPConnection receiveCon;

	EventObject lastevent;

	protected WiimoteStreamer(Wiimote wiimote) {
		this.wiimote = wiimote;
		this.receiveCon = wiimote.getReceiveConnection();
	}

    @Override
	public void run() {
		this.running = true;
		int xraw, yraw, zraw;
		double x, y, z;

		try {
			while (running) {
				// connection has data and we're ready.
				
				byte[] b = this.getRaw(); // blocks application

				// debug output
				/* for(int i=0; i<b.length; i++) {
				 * System.out.print((int)b[i]&0xFF); if(i==input.length-1) {
				 * System.out.println(""); } else { System.out.print(":"); } }
				 */
                

				// wiimote wants to tell the calibration data
				if ((b[1] & 0xFF) == 0x21) {
                    this.handleCalibrationData(
                            new byte[] {b[7], b[8], b[9], b[11], b[12], b[13]});
                }


				// infrared is enabled, so have a look at the last bytes
				if (this.wiimote.infraredEnabled()
						&& (b[1] & 0xFF) == 0x33) {
                    this.handleInfraredDate(
                            new byte[] {b[7], b[8], b[9],
                                        b[10], b[11], b[12],
                                        b[13], b[14], b[15],
                                        b[16], b[17], b[18]});
                }
                
                
				// if the wiimote is sending acceleration data...
				if (this.wiimote.accelerationEnabled()
                    && (    (((b[1] & 0xFF) & 0x31) == 0x31)
                         || (((b[1] & 0xFF) & 0x33) == 0x33)
                         || (((b[1] & 0xFF) & 0x35) == 0x35)
                         || (((b[1] & 0xFF) & 0x37) == 0x37)
                       )
                   ) {
                    this.handleAccelerationData(new byte[] { b[4], b[5], b[6] });
				}

                
                // process buttons
				this.handleButtonData(b[2], b[3]);


			} // while(running)

		} catch (IOException e) {
			Log.write("Streamer: Connection to Wiimote lost.");
			this.running = false;
		}
	}

    private void handleCalibrationData(byte[] data) {
        this.x0 = data[0] & 0xFF;
		this.y0 = data[1] & 0xFF;
		this.z0 = data[2] & 0xFF;
		this.x1 = data[3] & 0xFF;
		this.y1 = data[4] & 0xFF;
		this.z1 = data[5] & 0xFF;
		Log.write("Autocalibration successful!");
    }

    private void handleInfraredDate(byte[] data) {
        int[][] coordinates = new int[4][2];
		int[] size = new int[4];
		int j = 0;

		for (int i = 0; i < 12; i += 3) { // for each IR byte
														// triple
            int tailX = data[i] & 0xFF;
            int tailY = data[i + 1] & 0xFF;
            int preY = (data[i + 2] & 0xC0) << 2;
            int preX = (data[i + 2] & 0x30) << 4;

            coordinates[j][0] = tailX + preX;
            coordinates[j][1] = tailY + preY;
            size[j++] = (data[i + 2] & 0x0F);
        }

		this.wiimote.fireInfraredEvent(coordinates, size);
        
    }

    private void handleAccelerationData(byte[] data) {
        // convert to int.
        int xraw = (data[0] & 0xFF);
		int yraw = (data[1] & 0xFF);
		int zraw = (data[2] & 0xFF);

        // calculate acceleration with calibration data.
        double x = (double) (xraw - x0) / (double) (x1 - x0);
		double y = (double) (yraw - y0) / (double) (y1 - y0);
		double z = (double) (zraw - z0) / (double) (z1 - z0);

		this.wiimote.fireAccelerationEvent(new double[] {x, y, z});
    }

    private void handleButtonData(byte a, byte b) {
        // Button 1 matches button "2" on wiimote
				if (((b & 0xFF) & 0x01) == 0x01
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(1);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 1);
				}

				// Button 2 matches button "1" on wiimote
				else if (((b & 0xFF) & 0x02) == 0x02
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(2);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 2);
				}

				// Button 3 matches button "B" on wiimote
				else if (((b & 0xFF) & 0x04) == 0x04
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(3);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 3);
				}

				// Button 4 matches button "A" on wiimote
				else if (((b & 0xFF) & 0x08) == 0x08
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(4);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 4);
				}

				// Button 5 matches button "MINUS" on wiimote
				else if (((b & 0xFF) & 0x10) == 0x10
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(5);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 5);
				}

				// Button 6 unknown
				// Button 7 unknown

				// Button 8 matches button "HOME" on wiimote
				else if (((b & 0xFF) & 0x80) == 0x80
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(8);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 8);
				}

				// Button 9 matches "CROSS LEFT" on wiimote
				else if (((a & 0xFF) & 0x01) == 0x01
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(9);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 9);
				}

				// Button 10 matches "CROSS RIGHT" on wiimote
				else if (((a & 0xFF) & 0x02) == 0x02
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(10);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 10);
				}

				// Button 11 matches "CROSS DOWN" on wiimote
				else if (((a & 0xFF) & 0x04) == 0x04
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(11);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 11);
				}

				// Button 12 matches "CROSS UP" on wiimote
				else if (((a & 0xFF) & 0x08) == 0x08
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(12);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 12);
				}

				// Button 13 matches button "PLUS" on wiimote
				else if (((a & 0xFF) & 0x10) == 0x10
						&& !(this.lastevent instanceof ButtonPressedEvent)) {
					this.wiimote.fireButtonPressedEvent(13);
					this.lastevent = new ButtonPressedEvent(
							this.wiimote, 13);
				}

				// Button 14 unknown
				// Button 15 unknown
				// Button 16 unknown

                // Button release
                else if((      (((a & 0xFF) & 0x20) == 0x20)
                            || (((a & 0xFF) & 0x40) == 0x40)
                            || (((a & 0xFF) & 0x60) == 0x60)
                         ) &&
                         (
                               (((b & 0xFF) & 0x20) == 0x20)
                            || (((b & 0xFF) & 0x40) == 0x40)
                            || (((b & 0xFF) & 0x60) == 0x60)
                         ) &&
                        !(this.lastevent instanceof ButtonReleasedEvent)
                       ) {
                            this.wiimote.fireButtonReleasedEvent();
                            this.lastevent = new ButtonReleasedEvent(this.wiimote);
                         }
    }

	private byte[] getRaw() throws IOException {
		byte[] b = new byte[19];
		this.receiveCon.receive(b);
		return b;
	}

	/**
	 * stops this thread.
	 */
	protected void stopThread() {
		this.running = false;
	}

	protected boolean isRunning() {
		return this.running;
	}

	private String[] byte2hex(byte[] b) {
		String[] out = new String[b.length];
		String stmp = "";
		for (int n = 0; n < b.length; n++) {
			stmp = (java.lang.Integer.toHexString(b[n] & 0XFF));
			if (stmp.length() == 1)
				out[n] = ("0" + stmp).toUpperCase();
			else
				out[n] = stmp.toUpperCase();
		}
		return out;
	}

}
