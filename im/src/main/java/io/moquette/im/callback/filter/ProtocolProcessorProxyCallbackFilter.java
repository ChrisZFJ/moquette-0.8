package io.moquette.im.callback.filter;

import io.moquette.im.proxy.ProtocolProcessorProxy;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.CallbackFilter;

/**
 * ClassName: ProtocolProcessorProxyCallbackFilter 
 * @Description: ProtocolProcessor代理类的回调过滤器，负责调用多个相应的Callback实例
 * @author cuidonghuan
 * @date 2016年5月18日 下午6:35:22
 */

public class ProtocolProcessorProxyCallbackFilter implements CallbackFilter {

	/**
	 * accept()方法需要返回一个int类型结果,该int类型为上文中setCallbacks设置的多个
     * Callback处理逻辑的数组的下标
	 */
	@Override
	public int accept(Method method) {
		
		// Method参数代表代理类的执行方法, 
        // 以下logic为 判断执行方法名称是否为processDisconnect/processConnectionLost,
        // 是则执行OfflineCallbackImpl,也就是数组下标为1的逻辑, 否则执行NoOp.INSTANCE逻辑
		
		// 上线
		if("processConnect".equals(method.getName())) {
			return ProtocolProcessorProxy.ONLINE;
		}		
		
		// 离线
		/*if ("processDisconnect".equals(method.getName()) || "processConnectionLost".equals(method.getName())) {
			return ProtocolProcessorProxy.OFFLINE;
		}*/
		// Moquette中ProtocolProcessor中处理客户端断开连接应该是先调用processDisconnect()后调用processConnectionLost()（流程还未完全理解）
		// 判断method.getName()是否与上述两方法相等时会出现离线发送2次离线通知，暂定调整为只判断processDisconnect()方法...
		if ("processDisconnect".equals(method.getName())) {
			return ProtocolProcessorProxy.OFFLINE;
		}
		
		// 持久化IM消息对象
		if ("processPublish".equals(method.getName())) {
			return ProtocolProcessorProxy.PERSIST;
		}
		
		// 默认返回 ProtocolProcessorProxy.DEFAULT
		return ProtocolProcessorProxy.DEFAULT;
	}

}
