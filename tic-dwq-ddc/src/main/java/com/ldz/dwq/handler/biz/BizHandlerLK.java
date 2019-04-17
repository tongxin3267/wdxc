package com.ldz.dwq.handler.biz;

import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.springframework.stereotype.Component;

import com.ldz.dwq.bean.DeviceBean;
import com.ldz.dwq.common.bean.MessageBean;
import com.ldz.dwq.handler.ServerChannelInitializer;
import com.ldz.dwq.server.IotServer;

import io.netty.channel.ChannelHandlerContext;

/**
 * 终端心跳命令解析
 * @author Lee
 *
 */
@Component
public class BizHandlerLK extends BizBaseHandler {

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		MessageBean messageBean = (MessageBean)msg;
		
		//读取数据成功后，向终端响应结果
		String cid = ctx.channel().id().asShortText();
		String onlineKey = messageBean.getImei()+"-"+cid+"-"+IotServer.ONLINE_KEY;
		//更新终端设备功能
		DeviceBean device = (DeviceBean)redisDao.boundValueOps(onlineKey).get();
		accessLog.debug(onlineKey+"["+device+"]");
		if (device != null){
			device.setTime(DateTime.now().toString("yyyy-MM-dd HH:mm:ss"));
			redisDao.boundValueOps(onlineKey).set(device);
			redisDao.boundValueOps(onlineKey).expire(ServerChannelInitializer.READER_IDLE_TIME_SECONDS, TimeUnit.MINUTES);
			
			MessageBean sendData = new MessageBean();
			sendData.setMid(messageBean.getMid());
			sendData.setCommand(messageBean.getCommand());
			sendData.setImei(messageBean.getImei());
			
			iotServer.sendMsg(sendData);
		}
	}
}