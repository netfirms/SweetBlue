package com.idevicesinc.sweetblue;

import java.util.UUID;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.test.FlakyTest;
import android.webkit.JavascriptInterface;

import com.idevicesinc.sweetblue.BleDevice.BondListener;
import com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener;
import com.idevicesinc.sweetblue.BleDevice.ReadWriteListener;
import com.idevicesinc.sweetblue.BleDeviceConfig.TimeoutRequestFilter.TimeoutRequestEvent;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener.DiscoveryEvent;
import com.idevicesinc.sweetblue.BleManager.DiscoveryListener.LifeCycle;
import com.idevicesinc.sweetblue.annotations.*;
import com.idevicesinc.sweetblue.utils.*;

/**
 * Provides a number of options to (optionally) pass to {@link BleDevice#setConfig(BleDeviceConfig)}.
 * This class is also the super class of {@link BleManagerConfig}, which you can pass
 * to {@link BleManager#get(Context, BleManagerConfig)} to set default base options for all devices at once.
 * For all options in this class, you may set the value to <code>null</code> when passed to {@link BleDevice#setConfig(BleDeviceConfig)}
 * and the value will then be inherited from the {@link BleManagerConfig} passed to {@link BleManager#get(Context, BleManagerConfig)}.
 * Otherwise, if the value is not <code>null</code> it will override any option in the {@link BleManagerConfig}.
 * If an option is ultimately <code>null</code> (<code>null</code> when passed to {@link BleDevice#setConfig(BleDeviceConfig)}
 * *and* {@link BleManager#get(Context, BleManagerConfig)}) then it is interpreted as <code>false</code> or {@link Interval#DISABLED}.
 * <br><br>
 * TIP: You can use {@link Interval#DISABLED} instead of <code>null</code> to disable any time-based options, for code readability's sake.
 */
public class BleDeviceConfig implements Cloneable
{
	public static final double DEFAULT_MINIMUM_SCAN_TIME				= 5.0;
	public static final int DEFAULT_RUNNING_AVERAGE_N					= 10;
	public static final double DEFAULT_SCAN_KEEP_ALIVE					= DEFAULT_MINIMUM_SCAN_TIME*2.5;
	
	
	/**
	 * Default value for {@link #rssiAutoPollRate}.
	 */
	public static final double DEFAULT_RSSI_AUTO_POLL_RATE				= 10.0;
	
	/**
	 * Default fallback value for {@link #rssi_min}.
	 */
	public static final int DEFAULT_RSSI_MIN							= -120;
	
	/**
	 * Default fallback value for {@link #rssi_max}.
	 */
	public static final int DEFAULT_RSSI_MAX							= -30;

	/**
	 * Default value for {@link #defaultTxPower}.
	 */
	public static final int DEFAULT_TX_POWER							= -50;
	
	/**
	 * Status code used for {@link BleDevice.ReadWriteListener.ScanEvent#gattStatus} when the operation failed at a point where a
	 * gatt status from the underlying stack isn't provided or applicable.
	 * <br><br>
	 * Also used for {@link BleDevice.ConnectionFailListener.ConnectionFailEvent#gattStatus} for when the failure didn't involve the gatt layer.
	 */
	public static final int GATT_STATUS_NOT_APPLICABLE 					= -1;
	
	/**
	 * Used on {@link BleDevice.BondListener.BondEvent#failReason()} when {@link BleDevice.BondListener.BondEvent#status()}
	 * isn't applicable, for example {@link BleDevice.BondListener.Status#SUCCESS}.
	 */
	public static final int BOND_FAIL_REASON_NOT_APPLICABLE				= GATT_STATUS_NOT_APPLICABLE;
	
	/**
	 * As of now there are two main default uses for this class...
	 * <br><br>
	 * The first is that in at least some cases it's not possible to determine beforehand whether a given characteristic requires
	 * bonding, so implementing this interface on {@link BleManagerConfig#bondFilter} lets the app give
	 * a hint to the library so it can bond before attempting to read or write an encrypted characteristic.
	 * Providing these hints lets the library handle things in a more deterministic and optimized fashion, but is not required.
	 * <br><br>
	 * The second is that some android devices have issues when it comes to bonding. So far the worst culprits
	 * are certain Sony and Motorola phones, so if it looks like {@link Build#MANUFACTURER}
	 * is either one of those, {@link DefaultBondFilter} is set to unbond upon discoveries and disconnects.
	 * Please look at the source of {@link DefaultBondFilter} for the most up-to-date spec.
	 * The problem seems to be associated with mismanagement of pairing keys by the OS and
	 * this brute force solution seems to be the only way to smooth things out.
	 */
	@Advanced
	@Lambda
	public static interface BondFilter
	{
		/**
		 * Just a dummy subclass of {@link BleDevice.StateListener.StateEvent} so that this gets auto-imported for implementations of {@link BondFilter}. 
		 */
		@Advanced
		public static class StateChangeEvent extends BleDevice.StateListener.StateEvent
		{
			StateChangeEvent(BleDevice device, int oldStateBits, int newStateBits, int intentMask, int gattStatus)
			{
				super(device, oldStateBits, newStateBits, intentMask, gattStatus);
			}
		}
		
		/**
		 * An enumeration of the type of characteristic operation for a {@link CharacteristicEvent}.
		 */
		@Advanced
		public static enum CharacteristicEventType
		{
			/**
			 * Started from {@link BleDevice#read(UUID, ReadWriteListener)}, {@link BleDevice#startPoll(UUID, Interval, ReadWriteListener)}, etc.
			 */
			READ,
			
			/**
			 * Started from {@link BleDevice#write(UUID, byte[], ReadWriteListener)} or overloads.
			 */
			WRITE,
			
			/**
			 * Started from {@link BleDevice#enableNotify(UUID, ReadWriteListener)} or overloads.
			 */
			ENABLE_NOTIFY;
		}
		
		/**
		 * Struct passed to {@link BondFilter#onEvent(CharacteristicEvent)}.
		 */
		@Advanced
		@Immutable
		public static class CharacteristicEvent
		{
			/**
			 * Returns the {@link BleDevice} in question.
			 */
			public BleDevice device(){  return m_device;  }
			private final BleDevice m_device;
			
			/**
			 * Returns the {@link UUID} of the characteristic in question.
			 */
			public UUID charUuid(){  return m_uuid;  }
			private final UUID m_uuid;
			
			/**
			 * Returns the type of characteristic operation, read, write, etc.
			 */
			public CharacteristicEventType type(){  return m_type;  }
			private final CharacteristicEventType m_type;
			
			CharacteristicEvent(BleDevice device, UUID uuid, CharacteristicEventType type)
			{
				m_device = device;
				m_uuid = uuid;
				m_type = type;
			}
			
			@Override public String toString()
			{
				return Utils.toString
				(
					this.getClass(),
					"device",		device().getName_debug(),
					"charUuid",		device().getManager().getLogger().charName(charUuid()),
					"type",			type()
				);
			}
		}
		
		/**
		 * Return value for the various interface methods of {@link BondFilter}.
		 * Use static constructor methods to create instances.
		 */
		@Advanced
		@Immutable
		public static class Please
		{
			private final Boolean m_bond;
			private final BondListener m_bondListener;
			
			Please(Boolean bond, BondListener listener)
			{
				m_bond = bond;
				m_bondListener = listener;
			}
			
			Boolean bond_private()
			{
				return m_bond;
			}
			
			BondListener listener()
			{
				return m_bondListener;
			}
			
			/**
			 * Device should be bonded if it isn't already.
			 */
			public static Please bond()
			{
				return new Please(true, null);
			}
			
			/**
			 * Returns {@link #bond()} if the given condition holds <code>true</code>, {@link #doNothing()} otherwise.
			 */
			public static Please bondIf(boolean condition)
			{
				return condition ? bond() : doNothing();
			}
			
			/**
			 * Same as {@link #bondIf(boolean)} but lets you pass a {@link BondListener} as well.
			 */
			public static Please bondIf(boolean condition, BondListener listener)
			{
				return condition ? bond(listener) : doNothing();
			}
			
			/**
			 * Same as {@link #bond()} but lets you pass a {@link BondListener} as well.
			 */
			public static Please bond(BondListener listener)
			{
				return new Please(true, listener);
			}
			
			/**
			 * Device should be unbonded if it isn't already.
			 */
			public static Please unbond()
			{
				return new Please(false, null);
			}
			
			/**
			 * Returns {@link #bond()} if the given condition holds <code>true</code>, {@link #doNothing()} otherwise.
			 */
			public static Please unbondIf(boolean condition)
			{
				return condition ? unbond() : doNothing();
			}
			
			/**
			 * Device's bond state should not be affected.
			 */
			public static Please doNothing()
			{
				return new Please(null, null);
			}
		}
		
		/**
		 * Called after a device undergoes a change in its {@link BleDeviceState}.
		 */
		Please onEvent(StateChangeEvent event);
		
		/**
		 * Called immediately before reading, writing, or enabling notification on a characteristic.
		 */
		Please onEvent(CharacteristicEvent event);
	}
	
	/**
	 * Default implementation of {@link BondFilter} that unbonds for certain phone models upon discovery and disconnects.
	 * See further explanation in documentation for {@link BondFilter}.
	 */
	@Advanced
	@Immutable
	public static class DefaultBondFilter implements BondFilter
	{
		/**
		 * Forwards {@link Utils#phoneHasBondingIssues()}. Override to make this <code>true</code> for more (or fewer) phones.
		 */
		public boolean phoneHasBondingIssues()
		{
			return Utils.phoneHasBondingIssues();
		}

		@Override public Please onEvent(StateChangeEvent event)
		{
			if( phoneHasBondingIssues() )
			{
				if( event.didEnterAny(BleDeviceState.DISCOVERED, BleDeviceState.DISCONNECTED) )
				{
					return Please.unbond();
				}
			}
			
			return Please.doNothing();
		}

		@Override public Please onEvent(CharacteristicEvent event)
		{
			return Please.doNothing();
		}
	}
	
	/**
	 * An optional interface you can implement on {@link BleDeviceConfig#reconnectFilter } to control reconnection behavior.
	 * 
	 * @see #reconnectFilter
	 * @see DefaultReconnectFilter
	 */
	@Lambda
	public static interface ReconnectFilter
	{		
		/**
		 * Struct passed to {@link ReconnectFilter#onEvent(ReconnectFilter.ReconnectEvent)} to aid in making a decision.
		 */
		@Immutable
		public static class ReconnectEvent
		{
			/**
			 * The device that is currently {@link BleDeviceState#ATTEMPTING_RECONNECT}.
			 */
			public BleDevice device(){  return m_device;  }
			private final BleDevice m_device;
			
			/**
			 * The number of times a reconnect attempt has failed so far.
			 */
			public int failureCount(){  return m_failureCount;  }
			private final int m_failureCount;
			
			/**
			 * The total amount of time since the device went {@link BleDeviceState#DISCONNECTED} and we started the reconnect loop.
			 */
			public Interval totalTimeReconnecting(){  return m_totalTimeReconnecting;  }
			private final Interval m_totalTimeReconnecting;
			
			/**
			 * The previous {@link Interval} returned from {@link ReconnectFilter#onEvent(ReconnectEvent)}, or {@link Interval#ZERO}
			 * for the first invocation.
			 */
			public Interval previousDelay(){  return m_previousDelay;  }
			private final Interval m_previousDelay;
			
			/**
			 * Returns the more detailed information about why the connection failed. This is passed to {@link BleDevice.ConnectionFailListener#onEvent(com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.ConnectionFailEvent)}
			 * before the call is made to {@link ReconnectFilter#onEvent(ReconnectEvent)}. For the first call to {@link ReconnectFilter#onEvent(ReconnectEvent)},
			 * right after a spontaneous disconnect occurred, the connection didn't fail, so {@link ConnectionFailListener.ConnectionFailEvent#isNull()} will return <code>true</code>.
			 */
			public ConnectionFailListener.ConnectionFailEvent connectionFailInfo(){  return m_connectionFailInfo;  }
			private final ConnectionFailListener.ConnectionFailEvent m_connectionFailInfo;
			
			ReconnectEvent(BleDevice device, int failureCount, Interval totalTimeReconnecting, Interval previousDelay, ConnectionFailListener.ConnectionFailEvent connectionFailInfo)
			{
				this.m_device = device;
				this.m_failureCount = failureCount;
				this.m_totalTimeReconnecting = totalTimeReconnecting;
				this.m_previousDelay = previousDelay;
				this.m_connectionFailInfo = connectionFailInfo;
			}
			
			@Override public String toString()
			{
				return Utils.toString
				(
					this.getClass(),
					"device",					device().getName_debug(),
					"failureCount",				failureCount(),
					"totalTimeReconnecting",	totalTimeReconnecting(),
					"previousDelay",			previousDelay()
				);
			}
		}
		
		/**
		 * Return value for {@link ReconnectFilter#onEvent(ReconnectEvent)}. Use static constructor methods to create instances.
		 */
		@Immutable
		public static class Please
		{
			static final Interval INSTANTLY = Interval.ZERO;
			static final Interval STOP = Interval.DISABLED;
			
			private final Interval m_interval;
			
			private Please(Interval interval)
			{
				m_interval = interval;
			}
			
			Interval getInterval()
			{
				return m_interval;
			}
			
			/**
			 * Return this from {@link ReconnectFilter#onEvent(ReconnectFilter.ReconnectEvent)} to instantly reconnect.
			 */
			public static Please retryInstantly()
			{
				return new Please(INSTANTLY);
			}
			
			/**
			 * Return this from {@link ReconnectFilter#onEvent(ReconnectFilter.ReconnectEvent)} to stop a reconnect attempt loop.
			 * Note that {@link BleDevice#disconnect()} will also stop any ongoing reconnect loop.
			 */
			public static Please stopRetrying()
			{
				return new Please(STOP);
			}
			
			/**
			 * Return this from {@link ReconnectFilter#onEvent(ReconnectFilter.ReconnectEvent)} to retry after the given amount of time.
			 */
			public static Please retryIn(Interval interval)
			{
				return new Please(interval != null ? interval : INSTANTLY);
			}
		}
		
		/**
		 * Called for every connection failure while device is {@link BleDeviceState#ATTEMPTING_RECONNECT}.
		 * Use the static methods of {@link Please} as return values to stop reconnection ({@link Please#stopRetrying()}), try again
		 * instantly ({@link Please#retryInstantly()}), or after some amount of time {@link Please#retryIn(Interval)}.
		 */
		Please onEvent(ReconnectEvent event);
	}
	
	/**
	 * Default implementation of {@link ReconnectFilter} that uses {@link #DEFAULT_INITIAL_RECONNECT_DELAY}
	 * and {@link #DEFAULT_RECONNECT_ATTEMPT_RATE} to infinitely try to reconnect.
	 */
	public static class DefaultReconnectFilter implements ReconnectFilter
	{
		public static final Please DEFAULT_INITIAL_RECONNECT_DELAY = Please.retryInstantly();
		public static final Please DEFAULT_RECONNECT_ATTEMPT_RATE = Please.retryIn(Interval.secs(3.0));
		
		@Override public Please onEvent(ReconnectEvent event)
		{
			if( event.failureCount() == 0 )
			{
				return DEFAULT_INITIAL_RECONNECT_DELAY;
			}
			else
			{
				return DEFAULT_RECONNECT_ATTEMPT_RATE;
			}
		}
	}
	
	/**
	 * Provides a way to control timeout behavior for various {@link BleTask} instances.
	 */
	@Lambda
	@Advanced
	public static interface TimeoutRequestFilter
	{
		/**
		 * Event passed to {@link TimeoutRequestFilter#onEvent(TimeoutRequestEvent)} that provides
		 * information about the {@link BleTask} that will soon be executed.
		 */
		@Immutable
		public static class TimeoutRequestEvent
		{
			/**
			 * The {@link BleDevice} associated with the {@link #task()}, or {@link BleDevice#NULL} if
			 * {@link #task()} {@link BleTask#isManagerSpecific()} returns <code>true</code>.
			 */
			public BleDevice device(){  return m_device;  }
			private BleDevice m_device;
			
			/**
			 * Returns the manager.
			 */
			public BleManager manager(){  return m_manager;  }
			private BleManager m_manager;
			
			/**
			 * The type of task for which we are requesting a timeout.
			 */
			public BleTask task(){  return m_task;  }
			private BleTask m_task;
			
			/**
			 * The ble characteristic {@link UUID} associated with the task, or {@link Uuids#INVALID} otherwise.
			 */
			public UUID charUuid(){  return m_charUuid;  }
			private UUID m_charUuid;
			
			/**
			 * The ble descriptor {@link UUID} associated with the task, or {@link Uuids#INVALID} otherwise.
			 */
			public UUID descUuid(){  return m_descUuid;  }
			private UUID m_descUuid;
			
			void init(BleManager manager, BleDevice device, BleTask task, UUID charUuid, UUID descUuid)
			{
				m_manager = manager;
				m_device = device;
				m_task = task;
				m_charUuid = charUuid;
				m_descUuid = descUuid;
			}
		}
		
		/**
		 * Use static constructor methods to create instances to return from {@link TimeoutRequestFilter#onEvent(TimeoutRequestEvent)}.
		 */
		@Immutable
		public static class Please
		{
			private final Interval m_interval;
			
			Please(Interval interval)
			{
				m_interval = interval;
			}
			
			public static Please setTimeoutFor(final Interval interval)
			{
				return new Please(interval);
			}
			
			public static Please doNotUseTimeout()
			{
				return new Please(Interval.DISABLED);
			}
		}
		
		/**
		 * Implement this to have fine-grained control over {@link BleTask} timeout behavior.
		 */
		Please onEvent(TimeoutRequestEvent event);
	}
	
	/**
	 * Default implementation of {@link TimeoutRequestFilter} that simply sets the timeout
	 * for all {@link BleTask} instances to {@link #DEFAULT_TASK_TIMEOUT} seconds.
	 */
	public static class DefaultTimeoutRequestFilter implements TimeoutRequestFilter
	{
		public static final double DEFAULT_TASK_TIMEOUT						= 12.5;
		
		private static final Please RETURN_VALUE = Please.setTimeoutFor(Interval.secs(DEFAULT_TASK_TIMEOUT));
		
		@Override public Please onEvent(TimeoutRequestEvent event)
		{
			return RETURN_VALUE;
		}
	}
	
	/**
	 * Default is <code>true</code> - whether to automatically get services immediately after a {@link BleDevice} is
	 * {@link BleDeviceState#CONNECTED}. Currently this is the only way to get a device's services.
	 */
	public Boolean autoGetServices								= true;
	
	/**
	 * Default is <code>false</code>se - if true and you call {@link BleDevice#startPoll(UUID, Interval, BleDevice.ReadWriteListener)}
	 * or {@link BleDevice#startChangeTrackingPoll(UUID, Interval, BleDevice.ReadWriteListener)()} with identical
	 * parameters then two identical polls would run which would probably be wasteful and unintentional.
	 * This option provides a defense against that situation.
	 */
	public Boolean allowDuplicatePollEntries			= false;
	
	/**
	 * Default is <code>false</code> - {@link BleDevice#getAverageReadTime()} and {@link BleDevice#getAverageWriteTime()} can be 
	 * skewed if the peripheral you are connecting to adjusts its maximum throughput for OTA firmware updates and the like.
	 * Use this option to let the library know whether you want read/writes to factor in while {@link BleDeviceState#PERFORMING_OTA}.
	 * 
	 * @see BleDevice#getAverageReadTime()
	 * @see BleDevice#getAverageWriteTime() 
	 */
	public Boolean includeOtaReadWriteTimesInAverage		= false;
	
	/**
	 * Default is <code>false</code> - see the <code>boolean autoConnect</code> parameter of
	 * {@link BluetoothDevice#connectGatt(Context, boolean, android.bluetooth.BluetoothGattCallback)}. 
	 * 
	 * This parameter is one of Android's deepest mysteries. By default we keep it false, but it has been observed that a
	 * connection can fail or time out, but then if you try again with autoConnect set to true it works! One would think,
	 * why not always set it to true? Well, while true is anecdotally more stable, it also (anecdotally) makes for longer
	 * connection times, which becomes a UX problem. Would you rather have a 5-10 second connection process that is successful
	 * with 99% of devices, or a 1-2 second connection process that is successful with 95% of devices? By default we've chosen the latter.
	 * <br><br>
	 * HOWEVER, it's important to note that you can have fine-grained control over its usage through the {@link ConnectionFailListener.Please}
	 * returned from {@link ConnectionFailListener#onEvent(com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.ConnectionFailEvent)}.
	 * <br><br>
	 * So really this option mainly exists for those situations where you KNOW that you have a device that only works
	 * with autoConnect==true and you want connection time to be faster (i.e. you don't want to wait for that first
	 * failed connection for the library to internally start using autoConnect==true).
	 */
	@Advanced
	public Boolean alwaysUseAutoConnect						= false;
	
	/**
	 * Default is <code>true</code> - controls whether {@link BleManager} will keep a device in active memory when it goes {@link BleManagerState#OFF}.
	 * If <code>false</code> then a device will be purged and you'll have to do {@link BleManager#startScan()} again to discover devices
	 * if/when {@link BleManager} goes back {@link BleManagerState#ON}.
	 * <br><br>
	 * NOTE: if this flag is true for {@link BleManagerConfig} passed to {@link BleManager#get(Context, BleManagerConfig)} then this
	 * applies to all devices.
	 */
	@Advanced
	public Boolean retainDeviceWhenBleTurnsOff				= true;
	
	/**
	 * Default is <code>true</code> - only applicable if {@link #retainDeviceWhenBleTurnsOff} is also true. If {@link #retainDeviceWhenBleTurnsOff}
	 * is false then devices will be undiscovered when {@link BleManager} goes {@link BleManagerState#OFF} regardless.
	 * <br><br>
	 * NOTE: See NOTE for {@link #retainDeviceWhenBleTurnsOff} for how this applies to {@link BleManagerConfig}. 
	 * 
	 * @see #retainDeviceWhenBleTurnsOff
	 * @see #autoReconnectDeviceWhenBleTurnsBackOn
	 */
	@Advanced
	public Boolean undiscoverDeviceWhenBleTurnsOff			= true;
	
	/**
	 * Default is <code>true</code> - if devices are kept in memory for a {@link BleManager#turnOff()}/{@link BleManager#turnOn()} cycle
	 * (or a {@link BleManager#reset()}) because {@link #retainDeviceWhenBleTurnsOff} is <code>true</code>, then a {@link BleDevice#connect()}
	 * will be attempted for any devices that were previously {@link BleDeviceState#CONNECTED}.
	 * <br><br>
	 * NOTE: See NOTE for {@link #retainDeviceWhenBleTurnsOff} for how this applies to {@link BleManagerConfig}.
	 * 
	 * @see #retainDeviceWhenBleTurnsOff
	 */
	@Advanced
	public Boolean autoReconnectDeviceWhenBleTurnsBackOn 	= true;
	
	/**
	 * Default is <code>true</code> - controls whether the {@link State.ChangeIntent} behind a device going {@link BleDeviceState#DISCONNECTED}
	 * is saved to and loaded from disk so that it can be restored across app sessions, undiscoveries, and BLE
	 * {@link BleManagerState#OFF}->{@link BleManagerState#ON} cycles. This uses Android's {@link SharedPreferences} so does not require
	 * any extra permissions. The main advantage of this is the following scenario: User connects to a device through your app,
	 * does what they want, kills the app, then opens the app sometime later. {@link BleDevice#getLastDisconnectIntent()} returns
	 * {@link State.ChangeIntent#UNINTENTIONAL}, which lets you know that you can probably automatically connect to this device without user confirmation.
	 */
	@Advanced
	public Boolean manageLastDisconnectOnDisk				= true;
	
	/**
	 * Default is <code>true</code> - controls whether a {@link BleDevice} is placed into an in-memory cache when it becomes {@link BleDeviceState#UNDISCOVERED}.
	 * If <code>true</code>, subsequent calls to {@link BleManager.DiscoveryListener#onEvent(BleManager.DiscoveryListener.DiscoveryEvent)} with
	 * {@link LifeCycle#DISCOVERED} (or calls to {@link BleManager#newDevice(String)}) will return the cached {@link BleDevice} instead of creating a new one.
	 * <br><br>
	 * The advantages of caching are:<br>
	 * <ul>
	 * <li>Slightly better performance at the cost of some retained memory, especially in situations where you're frequently discovering and undiscovering devices.
	 * <li>Resistance to future stack failures that would otherwise mean missing data like {@link BleDevice#getAdvertisedServices()} for future discovery events.
	 * <li>More resistant to potential "user error" of retaining devices in app-land after BleManager undiscovery.
	 * <ul><br>
	 * This is kept as an option in case there's some unforeseen problem with devices being cached for a certain application.
	 * 
	 * See also {@link #minScanTimeNeededForUndiscovery}.
	 */
	@Advanced
	public Boolean cacheDeviceOnUndiscovery					= true;
	
	/**
	 * Default is <code>true</code> - controls whether {@link ConnectionFailListener.Status#BONDING_FAILED} is capable of
	 * inducing {@link ConnectionFailListener#onEvent(com.idevicesinc.sweetblue.BleDevice.ConnectionFailListener.ConnectionFailEvent)}
	 * while a device is {@link BleDeviceState#CONNECTING_OVERALL}.
	 */
	public Boolean bondingFailFailsConnection				= true;
	
	/**
	 * Default is <code>false</code> - whether to use {@link BluetoothGatt#refresh()} right before service discovery.
	 * This method is not in the public Android API, so its use is disabled by default. You may find it useful to enable
	 * if your remote device is routinely changing its gatt service profile. This method call supposedly clears a cache
	 * that would otherwise prevent changes from being discovered.
	 */
	@Advanced
	public Boolean useGattRefresh							= false;
	
	/**
	 * Default is {@link #DEFAULT_MINIMUM_SCAN_TIME} seconds - Undiscovery of devices must be
	 * approximated by checking when the last time was that we discovered a device,
	 * and if this time is greater than {@link #undiscoveryKeepAlive} then the device is undiscovered. However a scan
	 * operation must be allowed a certain amount of time to make sure it discovers all nearby devices that are
	 * still advertising. This is that time in seconds.
	 * <br><br>
	 * Use {@link Interval#DISABLED} to disable undiscovery altogether.
	 * 
	 * @see BleManager.DiscoveryListener#onEvent(DiscoveryEvent)
	 * @see #undiscoveryKeepAlive
	 */
	public Interval	minScanTimeNeededForUndiscovery				= Interval.secs(DEFAULT_MINIMUM_SCAN_TIME);
	
	/**
	 * Default is {@link #DEFAULT_SCAN_KEEP_ALIVE} seconds - If a device exceeds this amount of time since its
	 * last discovery then it is a candidate for being undiscovered.
	 * The default for this option attempts to accommodate the worst Android phones (BLE-wise), which may make it seem
	 * like it takes a long time to undiscover a device. You may want to configure this number based on the phone or
	 * manufacturer. For example, based on testing, in order to make undiscovery snappier the Galaxy S5 could use lower times.
	 * <br><br>
	 * Use {@link Interval#DISABLED} to disable undiscovery altogether.
	 * 
	 * @see BleManager.DiscoveryListener#onEvent(DiscoveryEvent)
	 * @see #minScanTimeNeededForUndiscovery
	 */
	public Interval	undiscoveryKeepAlive						= Interval.secs(DEFAULT_SCAN_KEEP_ALIVE);
	
	/**
	 * Default is {@link #DEFAULT_RSSI_AUTO_POLL_RATE} - The rate at which a {@link BleDevice} will automatically poll for its {@link BleDevice#getRssi()} value
	 * after it's {@link BleDeviceState#CONNECTED}. You may also use {@link BleDevice#startRssiPoll(Interval, ReadWriteListener)} for more control and feedback.
	 */
	public Interval rssiAutoPollRate							= Interval.secs(DEFAULT_RSSI_AUTO_POLL_RATE);
	
	/**
	 * Default is an array of {@link Interval} instances populated using {@link Interval#secs(double)} with {@link #DEFAULT_TASK_TIMEOUT}.
	 * This is an array of timeouts whose indices are meant to map to {@link BleTask#ordinal()} and provide a
	 * way to control how long a given task is allowed to run before being "cut loose". If no option is provided for a given {@link BleTask},
	 * either by setting this array <code>null</code>, or by providing <code>null</code> or {@link Interval#DISABLED} or {@link Interval#INFINITE} 
	 * for a given {@link BleTask}, then no timeout is observed.
	 * <br><br>
	 * TIP: Use {@link #setTimeout(Interval, BleTask...)} to modify this option more easily.
	 */
//	@Advanced
//	public Interval[] timeouts							= newTaskTimeArray();
//	{
//		final Interval defaultTimeout = Interval.secs(DEFAULT_TASK_TIMEOUT);
//		for( int i = 0; i < timeouts.length; i++ )
//		{
//			timeouts[i] = defaultTimeout;
//		}
//	}
	
	/**
	 * Default is {@link #DEFAULT_RUNNING_AVERAGE_N} - The number of historical write times that the library should keep track of when calculating average time.
	 * 
	 * @see BleDevice#getAverageWriteTime()
	 * @see #nForAverageRunningReadTime
	 */
	@Advanced
	public Integer		nForAverageRunningWriteTime			= DEFAULT_RUNNING_AVERAGE_N;
	
	/**
	 * Default is {@link #DEFAULT_RUNNING_AVERAGE_N} - Same thing as {@link #nForAverageRunningWriteTime} but for reads.
	 * 
	 * @see BleDevice#getAverageWriteTime()
	 * @see #nForAverageRunningWriteTime
	 */
	@Advanced
	public Integer		nForAverageRunningReadTime			= DEFAULT_RUNNING_AVERAGE_N;
	
	/**
	 * Default is {@link #DEFAULT_TX_POWER} - this value is used if we can't establish a device's calibrated transmission power from the device itself,
	 * either through its scan record or by reading the standard characteristic. To get a good value for this on a per-remote-device basis
	 * experimentally, simply run a sample app and use {@link BleDevice#startRssiPoll(Interval, ReadWriteListener)} and spit {@link BleDevice#getRssi()}
	 * to your log. The average value of {@link BleDevice#getRssi()} at one meter away is the value you should use for this config option.
	 * 
	 * @see BleDevice#getTxPower()
	 */
	@Advanced
	public Integer		defaultTxPower						= DEFAULT_TX_POWER;
	
	/**
	 * Default is {@link #DEFAULT_RSSI_MIN} - the estimated minimum value for {@link BleDevice#getRssi()}.
	 */
	public Integer		rssi_min							= DEFAULT_RSSI_MIN;
	
	/**
	 * Default is {@link #DEFAULT_RSSI_MAX} - the estimated maximum value for {@link BleDevice#getRssi()}.
	 */
	public Integer		rssi_max							= DEFAULT_RSSI_MAX;
	
	/**
	 * Default is instance of {@link DefaultBondFilter}.
	 * 
	 * @see BondFilter
	 */
	public BondFilter bondFilter							= new DefaultBondFilter();
	
	/**
	 * Default is an instance of {@link DefaultReconnectFilter} - set an implementation here to
	 * have fine control over reconnect behavior. This is basically how often and how long
	 * the library attempts to reconnect to a device that for example may have gone out of range. Set this variable to
	 * <code>null</code> if reconnect behavior isn't desired. If not <code>null</code>, your app may find
	 * {@link BleManagerConfig#manageCpuWakeLock} useful in order to force the app/phone to stay awake while attempting a reconnect.
	 * 
	 * @see BleManagerConfig#manageCpuWakeLock
	 * @see ReconnectFilter
	 * @see DefaultReconnectFilter
	 */
	public ReconnectFilter reconnectFilter					= new DefaultReconnectFilter();
	
	/**
	 * Default is an instance of {@link DefaultTimeoutRequestFilter} - set an implementation here to
	 * have fine control over how long individual {@link BleTask} instances can take before they
	 * are considered "timed out" and failed.
	 * <br><br>
	 * NOTE: Setting this to <code>null</code> will disable timeouts for all {@link BleTask} instances,
	 * which would probably be very dangerous to do - a task could just sit there spinning forever.
	 */
	@Advanced
	public TimeoutRequestFilter timeoutRequestFilter		= new DefaultTimeoutRequestFilter();
	
	static boolean boolOrDefault(Boolean bool_nullable)
	{
		return bool_nullable == null ? false : bool_nullable;
	}
	
	static Interval intervalOrDefault(Interval value_nullable)
	{
		return value_nullable == null ? Interval.DISABLED : value_nullable;
	}
	
	static boolean bool(Boolean bool_device_nullable, Boolean bool_mngr_nullable)
	{
		return bool_device_nullable != null ? bool_device_nullable : boolOrDefault(bool_mngr_nullable);
	}
	
	static Interval interval(Interval interval_device_nullable, Interval interval_mngr_nullable)
	{
		return interval_device_nullable != null ? interval_device_nullable : intervalOrDefault(interval_mngr_nullable);
	}
	
	static Integer integer(Integer int_device_nullable, Integer int_mngr_nullable)
	{
		return int_device_nullable != null ? int_device_nullable : int_mngr_nullable;
	}
	
	static Integer integer(Integer int_device_nullable, Integer int_mngr_nullable, int defaultValue)
	{
		return integerOrDefault(integer(int_device_nullable, int_mngr_nullable), defaultValue);
	}
	
	static int integerOrZero(Integer value_nullable)
	{
		return integerOrDefault(value_nullable, 0);
	}
	
	static int integerOrDefault(Integer value_nullable, int defaultValue)
	{
		return value_nullable != null ? value_nullable : defaultValue;
	}
	
	public BleDeviceConfig()
	{
	}
	
//	private static Interval getTaskInterval(final BleTask task, final Interval[] intervals_device_nullable, final Interval[] intervals_mngr_nullable)
//	{
//		final int ordinal = task.ordinal();
//		final Interval interval_device = intervals_device_nullable != null && intervals_device_nullable.length > ordinal ? intervals_device_nullable[ordinal] : null;
//		final Interval interval_mngr = intervals_mngr_nullable != null && intervals_mngr_nullable.length > ordinal ? intervals_mngr_nullable[ordinal] : null;
//		
//		return interval(interval_device, interval_mngr);
//	}
	
	static double getTimeout(final TimeoutRequestEvent event)
	{
		final BleManager manager = event.manager();
		final BleDevice device_nullable = !event.device().isNull() ? event.device() : null; 
		final TimeoutRequestFilter filter_device = device_nullable != null ? device_nullable.conf_device().timeoutRequestFilter : null;
		final TimeoutRequestFilter filter_mngr = manager.m_config.timeoutRequestFilter;
		final TimeoutRequestFilter filter = filter_device != null ? filter_device : filter_mngr;
		final TimeoutRequestFilter.Please please = filter != null ? filter.onEvent(event) : null;
		final Interval timeout = please != null ? please.m_interval : Interval.DISABLED;
		final double toReturn = timeout != null ? timeout.secs() : Interval.DISABLED.secs();
		
		return toReturn;
	}
	
	@Override protected BleDeviceConfig clone()
	{
		try
		{
			return (BleDeviceConfig) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
		}
		
		return null;
	}
}
