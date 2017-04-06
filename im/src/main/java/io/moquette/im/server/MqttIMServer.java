package io.moquette.im.server;

import io.moquette.im.callback.IPersistCallback;
import io.moquette.im.callback.IStateChangeCallback;
import io.moquette.im.processor.PersistProcessor;
import io.moquette.im.processor.StateChangeProcessor;
import io.moquette.im.proxy.ServerProxy;
import io.moquette.server.Server;

import java.io.IOException;

/**
 * ClassName: MqttIMServer 
 * @Description: Moquette IM Server启动类
 * @author cuidonghuan
 * @date 2016年5月17日 下午9:05:36
 */

public class MqttIMServer {
	
	private final Server server = ServerProxy.getInstance();
	
	public void startIMServer(IStateChangeCallback stateChangeCallback, IPersistCallback persistCallback) throws IOException, IllegalArgumentException {
		System.out.println("[MqttIMServer] MQTT IM SERVER START...");
		
		// 初始化处理器
		initProcessor(stateChangeCallback, persistCallback);
		
		// 启动Moquette MQTT Server...
		server.startServer();
	}
	
	/**
	 * @Description: 初始化处理器，获得StateChangeProcessor,PersistProcessor单例对象，并初始化其中的接口引用对象
	 * @param stateChangeCallback 状态改变接口实现类
	 * @param persistCallback 持久化接口实现类  
	 * @return void  
	 * @throws
	 * @author cuidonghuan
	 * @date 2016年5月19日 上午10:15:38
	 */
	private void initProcessor(IStateChangeCallback stateChangeCallback, IPersistCallback persistCallback) throws IllegalArgumentException {
		
		// 接口实现类不能为null
		if (null == stateChangeCallback || null == persistCallback) {
			throw new IllegalArgumentException("Callback实现类不能为空!");
		}
		
		// 获得Processor单例对象, 之后需要调用单例对象使用:xxProcessor.getInstance(null);
		StateChangeProcessor.getInstance(stateChangeCallback);
		PersistProcessor.getInstance(persistCallback);
	}
	
	public void stopIMServer() {
		// 关闭Moquette MQTT Server...
		server.stopServer();
	}
	
	public static void main(String[] args) throws IOException {
		MqttIMServer server = new MqttIMServer();
		server.startIMServer(null, null);
	}
}
