package io.moquette.im.proxy;

import io.moquette.im.callback.filter.ProtocolProcessorProxyCallbackFilter;
import io.moquette.im.callback.impl.OfflineCallback;
import io.moquette.im.callback.impl.OnlineCallback;
import io.moquette.im.callback.impl.PersistCallback;
import io.moquette.spi.impl.ProtocolProcessor;

import java.lang.reflect.Method;

import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.NoOp;

/**
 * ClassName: ProtocolProcessorProxy 
 * @Description: 对ProtocolProcessor对象进行代理,以实现IM的状态同步逻辑.
 * @author cuidonghuan
 * @date 2016年5月16日 下午4:57:52
 */

public class ProtocolProcessorProxy implements MethodInterceptor {
	
	/** 默认动作 */
	public static final int DEFAULT = 0;
	
	/** 用户上线时 */
	public static final int ONLINE = 1;
	
	/** 用户离线时 */
	public static final int OFFLINE = 2;
	
	/** 用户发送IM消息(私聊、群聊)时 */
	public static final int PERSIST = 3;
	
	/** ProtocolProcessor代理对象单例 */
	private static ProtocolProcessor protocolProcessorInstance = null;
	
	/**
	 * @Description: 获得ProtocolProcessor单例对象
	 * @return ProtocolProcessor  
	 * @author cuidonghuan
	 * @date 2016年5月19日 下午2:28:29
	 */
	public static ProtocolProcessor getInstance() {
		if(null == protocolProcessorInstance) {
			protocolProcessorInstance = getProtocolProcessorProxy();
		}
		return protocolProcessorInstance;
	}
	
	/**
	 * 获得代理对象
	 * @param clazz ProtocolProcessor的委托类
	 */
	private static ProtocolProcessor getProtocolProcessorProxy() {
		
		// 回调接口集合定义
		// 下标为0的Callback为NoOp.INSTANCE，NoOp实现类会使用默认的父类实现,没增加任何逻辑处理
		Callback[] callbacks = new Callback[]{NoOp.INSTANCE, new OnlineCallback(), new OfflineCallback(), new PersistCallback()};
		
		Enhancer enhancer = new Enhancer();
		// 设置需要创建子类的类 (超类)
		enhancer.setSuperclass(ProtocolProcessor.class);
		enhancer.setCallbacks(callbacks);
		// 设置回调过滤器
		enhancer.setCallbackFilter(new ProtocolProcessorProxyCallbackFilter());
				
		// 通过字节码技术动态创建子类实例  
		return (ProtocolProcessor) enhancer.create();
		
	}

	@Override
	public Object intercept(Object object, Method method, Object[] args,
			MethodProxy proxy) throws Throwable {
		proxy.invokeSuper(object, args);
		return null;
	}

}
