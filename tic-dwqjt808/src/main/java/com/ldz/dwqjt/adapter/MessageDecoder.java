package com.ldz.dwqjt.adapter;


import com.ldz.dwqjt.jt808.util.BCD8421Operater;
import com.ldz.dwqjt.jt808.util.BitOperator;
import com.ldz.dwqjt.jt808.util.Consts;
import com.ldz.dwqjt.jt808.util.JT808ProtocolUtils;
import com.ldz.dwqjt.jt808.vo.PackageData;
import com.ldz.dwqjt.jt808.vo.req.LocationInfoUploadMsg;
import com.ldz.dwqjt.jt808.vo.req.TerminalRegisterMsg;
import com.ldz.util.commonUtil.DateUtils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

/**
 * 数据解码器，将接收到的字节数据，按照协议格式封装成bean结构
 *
 */
public class MessageDecoder extends LengthFieldBasedFrameDecoder {

    Logger accessLog = LoggerFactory.getLogger("access_info");
    Logger errorLog = LoggerFactory.getLogger("error_info");
    
    private BitOperator bitOperator;
	private BCD8421Operater bcd8421Operater;
	private JT808ProtocolUtils util;

    public MessageDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
    	super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    	
    	this.bitOperator = new BitOperator();
		this.bcd8421Operater = new BCD8421Operater();
		this.util = new JT808ProtocolUtils();
	}

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        if (in == null) {
            return null;
        }
        int length = in.readableBytes();
        ByteBuf dataBuf = in.readBytes(length);
        String dataStr = ByteBufUtil.hexDump(dataBuf).toUpperCase();
        accessLog.info("通道["+ctx.channel().id().asShortText()+"]接收数据:"+dataStr);
        PackageData ret = new PackageData();

		// 0. 终端套接字地址信息
		// ret.setChannel(msg.getChannel());

        byte[] data = ByteBufUtil.getBytes(dataBuf);
        //将接收到的数据做一下转义处理
        data = util.doEscape4Receive(data, 0, data.length);
		// 1. 16byte 或 12byte 消息头
		PackageData.MsgHeader msgHeader = this.parseMsgHeaderFromBytes(data);
		ret.setMsgHeader(msgHeader);

		int msgBodyByteStartIndex = 12;
		// 2. 消息体
		// 有子包信息,消息体起始字节后移四个字节:消息包总数(word(16))+包序号(word(16))
		if (msgHeader.isHasSubPackage()) {
			msgBodyByteStartIndex = 16;
		}
		byte[] tmp = new byte[msgHeader.getMsgBodyLength()];
		System.arraycopy(data, msgBodyByteStartIndex, tmp, 0, tmp.length);
		ret.setMsgBodyBytes(tmp);

		// 3. 去掉分隔符之后，最后一位就是校验码
		// int checkSumInPkg =
		// this.bitOperator.oneByteToInteger(data[data.length - 1]);
		int checkSumInPkg = data[data.length - 1];
		int calculatedCheckSum = this.bitOperator.getCheckSum4JT808(data, 0, data.length - 1);
		ret.setCheckSum(checkSumInPkg);
		if (checkSumInPkg != calculatedCheckSum) {
			//log.warn("检验码不一致,msgid:{},pkg:{},calculated:{}", msgHeader.getMsgId(), checkSumInPkg, calculatedCheckSum);
		}
		
		return ret;
    }

    private PackageData.MsgHeader parseMsgHeaderFromBytes(byte[] data) {
		PackageData.MsgHeader msgHeader = new PackageData.MsgHeader();

		// 1. 消息ID word(16)
		// byte[] tmp = new byte[2];
		// System.arraycopy(data, 0, tmp, 0, 2);
		// msgHeader.setMsgId(this.bitOperator.twoBytesToInteger(tmp));
		msgHeader.setMsgId(this.parseIntFromBytes(data, 0, 2));

		// 2. 消息体属性 word(16)=================>
		// System.arraycopy(data, 2, tmp, 0, 2);
		// int msgBodyProps = this.bitOperator.twoBytesToInteger(tmp);
		int msgBodyProps = this.parseIntFromBytes(data, 2, 2);
		msgHeader.setMsgBodyPropsField(msgBodyProps);
		// [ 0-9 ] 0000,0011,1111,1111(3FF)(消息体长度)
		msgHeader.setMsgBodyLength(msgBodyProps & 0x3ff);
		// [10-12] 0001,1100,0000,0000(1C00)(加密类型)
		msgHeader.setEncryptionType((msgBodyProps & 0x1c00) >> 10);
		// [ 13_ ] 0010,0000,0000,0000(2000)(是否有子包)
		msgHeader.setHasSubPackage(((msgBodyProps & 0x2000) >> 13) == 1);
		// [14-15] 1100,0000,0000,0000(C000)(保留位)
		msgHeader.setReservedBit(((msgBodyProps & 0xc000) >> 14) + "");
		// 消息体属性 word(16)<=================

		// 3. 终端手机号 bcd[6]
		// tmp = new byte[6];
		// System.arraycopy(data, 4, tmp, 0, 6);
		// msgHeader.setTerminalPhone(this.bcd8421Operater.bcd2String(tmp));
		msgHeader.setTerminalPhone(this.parseBcdStringFromBytes(data, 4, 6));

		// 4. 消息流水号 word(16) 按发送顺序从 0 开始循环累加
		// tmp = new byte[2];
		// System.arraycopy(data, 10, tmp, 0, 2);
		// msgHeader.setFlowId(this.bitOperator.twoBytesToInteger(tmp));
		msgHeader.setFlowId(this.parseIntFromBytes(data, 10, 2));

		// 5. 消息包封装项
		// 有子包信息
		if (msgHeader.isHasSubPackage()) {
			// 消息包封装项字段
			msgHeader.setPackageInfoField(this.parseIntFromBytes(data, 12, 4));
			// byte[0-1] 消息包总数(word(16))
			// tmp = new byte[2];
			// System.arraycopy(data, 12, tmp, 0, 2);
			// msgHeader.setTotalSubPackage(this.bitOperator.twoBytesToInteger(tmp));
			msgHeader.setTotalSubPackage(this.parseIntFromBytes(data, 12, 2));

			// byte[2-3] 包序号(word(16)) 从 1 开始
			// tmp = new byte[2];
			// System.arraycopy(data, 14, tmp, 0, 2);
			// msgHeader.setSubPackageSeq(this.bitOperator.twoBytesToInteger(tmp));
			msgHeader.setSubPackageSeq(this.parseIntFromBytes(data, 12, 2));
		}
		return msgHeader;
	}

	protected String parseStringFromBytes(byte[] data, int startIndex, int lenth) {
		return this.parseStringFromBytes(data, startIndex, lenth, null);
	}

	private String parseStringFromBytes(byte[] data, int startIndex, int lenth, String defaultVal) {
		try {
			byte[] tmp = new byte[lenth];
			System.arraycopy(data, startIndex, tmp, 0, lenth);
			return new String(tmp, Consts.string_charset);
		} catch (Exception e) {
			errorLog.error("解析字符串出错:{}", e.getMessage());
			e.printStackTrace();
			return defaultVal;
		}
	}

	private String parseBcdStringFromBytes(byte[] data, int startIndex, int lenth) {
		return this.parseBcdStringFromBytes(data, startIndex, lenth, null);
	}

	private String parseBcdStringFromBytes(byte[] data, int startIndex, int lenth, String defaultVal) {
		try {
			byte[] tmp = new byte[lenth];
			System.arraycopy(data, startIndex, tmp, 0, lenth);
			return this.bcd8421Operater.bcd2String(tmp);
		} catch (Exception e) {
			errorLog.error("解析BCD(8421码)出错:{}", e.getMessage());
			e.printStackTrace();
			return defaultVal;
		}
	}

	private int parseIntFromBytes(byte[] data, int startIndex, int length) {
		return this.parseIntFromBytes(data, startIndex, length, 0);
	}

	private int parseIntFromBytes(byte[] data, int startIndex, int length, int defaultVal) {
		try {
			// 字节数大于4,从起始索引开始向后处理4个字节,其余超出部分丢弃
			final int len = length > 4 ? 4 : length;
			byte[] tmp = new byte[len];
			System.arraycopy(data, startIndex, tmp, 0, len);
			return bitOperator.byteToInteger(tmp);
		} catch (Exception e) {
			errorLog.error("解析整数出错:{}", e.getMessage());
			e.printStackTrace();
			return defaultVal;
		}
	}

	public TerminalRegisterMsg toTerminalRegisterMsg(PackageData packageData) {
		TerminalRegisterMsg ret = new TerminalRegisterMsg(packageData);
		byte[] data = ret.getMsgBodyBytes();

		TerminalRegisterMsg.TerminalRegInfo body = new TerminalRegisterMsg.TerminalRegInfo();

		// 1. byte[0-1] 省域ID(WORD)
		// 设备安装车辆所在的省域，省域ID采用GB/T2260中规定的行政区划代码6位中前两位
		// 0保留，由平台取默认值
		body.setProvinceId(this.parseIntFromBytes(data, 0, 2));

		// 2. byte[2-3] 设备安装车辆所在的市域或县域,市县域ID采用GB/T2260中规定的行 政区划代码6位中后四位
		// 0保留，由平台取默认值
		body.setCityId(this.parseIntFromBytes(data, 2, 2));

		// 3. byte[4-8] 制造商ID(BYTE[5]) 5 个字节，终端制造商编码
		// byte[] tmp = new byte[5];
		body.setManufacturerId(this.parseStringFromBytes(data, 4, 5));

		// 4. byte[9-16] 终端型号(BYTE[8]) 八个字节， 此终端型号 由制造商自行定义 位数不足八位的，补空格。
		body.setTerminalType(this.parseStringFromBytes(data, 9, 8));

		// 5. byte[17-23] 终端ID(BYTE[7]) 七个字节， 由大写字母 和数字组成， 此终端 ID由制造 商自行定义
		body.setTerminalId(this.parseStringFromBytes(data, 17, 7));

		// 6. byte[24] 车牌颜色(BYTE) 车牌颜 色按照JT/T415-2006 中5.4.12 的规定
		body.setLicensePlateColor(this.parseIntFromBytes(data, 24, 1));

		// 7. byte[25-x] 车牌(STRING) 公安交 通管理部门颁 发的机动车号牌
		body.setLicensePlate(this.parseStringFromBytes(data, 25, data.length - 25));

		ret.setTerminalRegInfo(body);
		return ret;
	}


	public LocationInfoUploadMsg toLocationInfoUploadMsg(PackageData packageData) {
		LocationInfoUploadMsg ret = new LocationInfoUploadMsg(packageData);
		final byte[] data = ret.getMsgBodyBytes();

		// 1. byte[0-3] 报警标志(DWORD(32))
		ret.setWarningFlagField(this.parseIntFromBytes(data, 0, 3));
		// 2. byte[4-7] 状态(DWORD(32))
		ret.setStatusField(this.parseIntFromBytes(data, 4, 4));
		// 3. byte[8-11] 纬度(DWORD(32)) 以度为单位的纬度值乘以10^6，精确到百万分之一度
		ret.setLatitude(this.parseFloatFromBytes(data, 8, 4));
		// 4. byte[12-15] 经度(DWORD(32)) 以度为单位的经度值乘以10^6，精确到百万分之一度
		ret.setLongitude(this.parseFloatFromBytes(data, 12, 4));
		// 5. byte[16-17] 高程(WORD(16)) 海拔高度，单位为米（ m）
		ret.setElevation(this.parseIntFromBytes(data, 16, 2));
		// byte[18-19] 速度(WORD) 1/10km/h
		ret.setSpeed(this.parseIntFromBytes(data, 18, 2));
		// byte[20-21] 方向(WORD) 0-359，正北为 0，顺时针
		ret.setDirection(this.parseIntFromBytes(data, 20, 2));
		// byte[22-x] 时间(BCD[6]) YY-MM-DD-hh-mm-ss
		// GMT+8 时间，本标准中之后涉及的时间均采用此时区
		byte[] tmp = new byte[6];
		System.arraycopy(data, 22, tmp, 0, 6);
		String time = this.parseBcdStringFromBytes(data, 22, 6);
		try {
			ret.setTime(DateUtils.getDate(time, "yyMMddHHmmss"));
		} catch (ParseException e) {
		}
		return ret;
	}

	private float parseFloatFromBytes(byte[] data, int startIndex, int length) {
		return this.parseFloatFromBytes(data, startIndex, length, 0f);
	}

	private float parseFloatFromBytes(byte[] data, int startIndex, int length, float defaultVal) {
		try {
			// 字节数大于4,从起始索引开始向后处理4个字节,其余超出部分丢弃
			final int len = length > 4 ? 4 : length;
			byte[] tmp = new byte[len];
			System.arraycopy(data, startIndex, tmp, 0, len);
			return bitOperator.byte2Float(tmp);
		} catch (Exception e) {
			errorLog.error("解析浮点数出错:{}", e.getMessage());
			e.printStackTrace();
			return defaultVal;
		}
	}
}