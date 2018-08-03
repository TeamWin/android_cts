package com.android.cts.omapi.test;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.Util;

public class CtsAndroidOmapiTestApplet extends Applet {

	final private static byte NO_DATA_INS_1 = (byte) 0x06;
	final private static byte NO_DATA_INS_2 = (byte) 0x0A;

	final private static byte DATA_INS_1 = (byte) 0x08;
	final private static byte DATA_INS_2 = (byte) 0xC0;

	final private static byte SW_62xx_APDU_INS = (byte) 0xF3;
	final private static byte SW_62xx_DATA_APDU_P2 = (byte) 0x08;
	final private static byte SW_62xx_VALIDATE_DATA_P2 = (byte) 0x0C;
	private final static byte[] SW_62xx_VALIDATE_DATA_RESP =
            new byte[]{0x01, (byte) 0xF3, 0x00, 0x0C, 0x01, (byte) 0xAA, 0x00};
	private final static short[] SW_62xx_resp = new short[]{
			(short)0x6200, (short)0x6281, (short)0x6282, (short)0x6283,
			(short)0x6285, (short)0x62F1, (short)0x62F2, (short)0x63F1,
			(short)0x63F2, (short)0x63C2, (short)0x6202, (short)0x6280,
			(short)0x6284, (short)0x6286, (short)0x6300, (short)0x6381,
	};

	final public static byte SEGMENTED_RESP_INS_1 = (byte) 0xC2;
	final public static byte SEGMENTED_RESP_INS_2 = (byte) 0xC4;
	final public static byte SEGMENTED_RESP_INS_3 = (byte) 0xC6;
	final public static byte SEGMENTED_RESP_INS_4 = (byte) 0xC8;
	final public static byte SEGMENTED_RESP_INS_5 = (byte) 0xCF;

	final private static byte CHECK_SELECT_P2_APDU = (byte)0xF4;
	final public static byte GET_RESPONSE_INS = (byte) 0xC0;

	final private static byte BER_TLV_TYPE = (byte) 0x1F;
	final private static short SELECT_RESPONSE_DATA_LENGTH = (short)252;

	private byte[] respBuf;
	private short respBuffOffset = 0;

	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// GP-compliant JavaCard applet registration
		new com.android.cts.omapi.test.CtsAndroidOmapiTestApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
	}

	public void process(APDU apdu) {
		byte[] buf = apdu.getBuffer();
		short le, lc;
		short sendLen;
		byte p1 = buf[ISO7816.OFFSET_P1];
		byte p2 = buf[ISO7816.OFFSET_P2];

		if (selectingApplet()) {
			lc = buf[ISO7816.OFFSET_LC];
			if (buf[(short)(lc + ISO7816.OFFSET_CDATA - 1)]  == 0x31) {
				//AID: A000000476416E64726F696443545331
				ISOException.throwIt(ISO7816.SW_NO_ERROR);
			} else {
				//A000000476416E64726F696443545332
				sendLen = fillBerTLVBytes(SELECT_RESPONSE_DATA_LENGTH);
				le = apdu.setOutgoing();
				apdu.setOutgoingLength((short) sendLen);
				Util.arrayCopy(respBuf, (short) 0, buf, (short) 0,
				        (short) respBuf.length);
				apdu.sendBytesLong(buf, respBuffOffset, sendLen);
				return;
			}
		}
		switch (buf[ISO7816.OFFSET_INS]) {
		case NO_DATA_INS_1:
		case NO_DATA_INS_2:
			ISOException.throwIt(ISO7816.SW_NO_ERROR);
			break;
		case DATA_INS_2:
			apdu.setIncomingAndReceive();
	        lc = apdu.getIncomingLength();
			//conflict with ISO Get Response command
			if (lc > 0) {
				//if case 3 APDU, return 256 bytes data
				sendLen = fillBytes((short)256);
				le = apdu.setOutgoing();
				apdu.setOutgoingLength((short) sendLen);
				Util.arrayCopy(respBuf, (short) 0, buf, (short) 0,
				        (short) respBuf.length);
				apdu.sendBytesLong(buf, respBuffOffset, sendLen);
			} else {
				//ISO GET_RESPONSE command
				if (respBuf == null) {
					ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
				} else {
					le = apdu.setOutgoing();
					sendLen = (short) (respBuf.length - respBuffOffset);
					sendLen = le > sendLen ? sendLen : le;
					apdu.setOutgoingLength(sendLen);
					apdu.sendBytesLong(respBuf, respBuffOffset, sendLen);
					respBuffOffset += sendLen;
					sendLen = (short) (respBuf.length - respBuffOffset);
					if (sendLen > 0) {
						if (sendLen > 256) sendLen = 0x00;
						ISOException.throwIt((short) (ISO7816.SW_BYTES_REMAINING_00 | sendLen));
					} else {
						respBuf = null;
					}
				}
			}
			break;

		case DATA_INS_1:
			// return 256 bytes data
			sendLen = fillBytes((short)256);
			le = apdu.setOutgoing();
			apdu.setOutgoingLength((short) sendLen);
			Util.arrayCopy(respBuf, (short) 0, buf, (short) 0,
			        (short) respBuf.length);
			apdu.sendBytesLong(buf, respBuffOffset, sendLen);
			break;
		case SW_62xx_APDU_INS:
			if (p1 > 16 || p1 < 1) {
				ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
			} else if (p2 == SW_62xx_DATA_APDU_P2){
				sendLen = fillBytes((short)3);
				le = apdu.setOutgoing();
				apdu.setOutgoingLength((short) sendLen);
				Util.arrayCopy(respBuf, (short) 0, buf, (short) 0,
				        (short) respBuf.length);
				apdu.sendBytesLong(buf, respBuffOffset, sendLen);
				ISOException.throwIt(SW_62xx_resp[p1 -1]);
			} else if (p2 == SW_62xx_VALIDATE_DATA_P2){
				le = apdu.setOutgoing();
				apdu.setOutgoingLength((short) (SW_62xx_VALIDATE_DATA_RESP.length));
				Util.arrayCopy(SW_62xx_VALIDATE_DATA_RESP, (short) 0, buf, (short) 0,
				        (short) SW_62xx_VALIDATE_DATA_RESP.length);
				buf[ISO7816.OFFSET_P1] = p1;
				apdu.sendBytesLong(buf, (short)0, (short) SW_62xx_VALIDATE_DATA_RESP.length);
				ISOException.throwIt(SW_62xx_resp[p1 -1]);
			} else {
				ISOException.throwIt(SW_62xx_resp[p1 -1]);
			}
			break;
		case SEGMENTED_RESP_INS_1:
		case SEGMENTED_RESP_INS_2:
			le = (short)((short)((p1 & 0xFF)<< 8) | (short)(p2 & 0xFF));
			sendLen = fillBytes(le);
			le = apdu.setOutgoing();
			sendLen = le > sendLen ? sendLen : le;
			if (sendLen > 0xFF) sendLen = 0xFF;
			apdu.setOutgoingLength(sendLen);
			apdu.sendBytesLong(respBuf, respBuffOffset, sendLen);
			respBuffOffset += sendLen;
			sendLen = (short) (respBuf.length - respBuffOffset);
			if (sendLen > 0) {
				if (sendLen > 256) sendLen = 0x00;
				ISOException.throwIt((short) (ISO7816.SW_BYTES_REMAINING_00 | sendLen));
			}
			break;
		case SEGMENTED_RESP_INS_3:
		case SEGMENTED_RESP_INS_4:
			le = (short)((short)((p1 & 0xFF)<< 8) | (short)(p2 & 0xFF));
			sendLen = fillBytes(le);
			le = apdu.setOutgoing();
			sendLen = le > sendLen ? sendLen : le;
			apdu.setOutgoingLength(sendLen);
			apdu.sendBytesLong(respBuf, respBuffOffset, sendLen);
			respBuffOffset += sendLen;
			sendLen = (short) (respBuf.length - respBuffOffset);
			if (sendLen > 0) {
				if (sendLen > 256) sendLen = 0x00;
				ISOException.throwIt((short) (ISO7816.SW_BYTES_REMAINING_00 | sendLen));
			}
			break;

		case SEGMENTED_RESP_INS_5:
			le = apdu.setOutgoing();
			if (le != 0xFF) {
				short buffer_len = (short)((short)((p1 & 0xFF)<< 8) | (short)(p2 & 0xFF));
				sendLen = fillBytes(buffer_len);
				sendLen = le > sendLen ? sendLen : le;
				apdu.setOutgoingLength(sendLen);
				apdu.sendBytesLong(respBuf, respBuffOffset, sendLen);
				respBuffOffset += sendLen;
				sendLen = (short) (respBuf.length - respBuffOffset);
				if (sendLen > 0) {
					if (sendLen > 256) sendLen = 0x00;
					ISOException.throwIt((short) (ISO7816.SW_BYTES_REMAINING_00 | sendLen));
				}
			} else {
				ISOException.throwIt((short) (ISO7816.SW_CORRECT_LENGTH_00 | 0xFF));
			}
			break;
		case CHECK_SELECT_P2_APDU:
			byte[] p2_00 = new byte[] {0x00};
			le = apdu.setOutgoing();
			apdu.setOutgoingLength((short) p2_00.length);
			Util.arrayCopy(p2_00, (short) 0, buf, (short) 0,
			        (short) p2_00.length);
			apdu.sendBytesLong(buf, (short)0, (short)p2_00.length);
			break;
		default:
			// Case is not known.
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}

	/**
	 * Fills APDU buffer with a pattern.
	 *
	 * @param le - length of outgoing data.
	 */
	public short fillBerTLVBytes(short le) {
		//support length from 0x00 - 0x7FFF
		short total_len;
		short le_len = 1;
		if (le < (short)0x80) {
			le_len = 1;
		} else if (le < (short)0x100) {
			le_len = 2;
		} else {
			le_len = 3;
		}
		total_len = (short)(le + 2 + le_len);
		short i = 0;
		respBuf = new byte[total_len];
		respBuf[(short)i] = BER_TLV_TYPE;
		i = (short)(i + 1);
		respBuf[(short)i] = 0x00; //second byte of Type
		i = (short)(i + 1);
		if (le < (short)0x80) {
			respBuf[(short)i] = (byte)le;
			i = (short)(i + 1);
		} else if (le < (short)0x100) {
			respBuf[(short)i] = (byte)0x81;
			i = (short)(i + 1);
			respBuf[(short)i] = (byte)le;
			i = (short)(i + 1);
		} else {
			respBuf[(short)i] = (byte)0x82;
			i = (short)(i + 1);
			respBuf[(short)i] = (byte)(le >> 8);
			i = (short)(i + 1);
			respBuf[(short)i] = (byte)(le & 0xFF);
			i = (short)(i + 1);
		}
		while (i < total_len) {
			respBuf[i] = (byte)((i - 2 - le_len) & 0xFF);
			i = (short)(i + 1);
		}
		respBuf[(short)(respBuf.length - 1)] = (byte)0xFF;
		respBuffOffset = (short) 0;
		return total_len;
	}

	public short fillBytes(short total_len) {
		short i = 0;
		respBuf = new byte[total_len];
		while (i < total_len) {
			respBuf[i] = (byte)(i & 0xFF);
			i = (short)(i + 1);
		}
		respBuffOffset = (short) 0;
		//set the last byte to 0xFF for CTS validation
		respBuf[(short)(respBuf.length - 1)] = (byte)0xFF;
		return total_len;
	}
}
