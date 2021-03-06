package com.idevicesinc.sweetblue;

import java.util.List;


/**
 * 
 * 
 *
 */
class PU_TaskQueue
{
	static int findSoonestSpot(List<PA_Task> taskList, PA_Task taskToAdd)
	{
		if( taskList.size() == 0 )  return 0;
		
		for( int i = 0; i < taskList.size(); i++ )
		{
			PA_Task ithQueuedTask = taskList.get(i);
			
			if( taskToAdd.isMoreImportantThan(ithQueuedTask) )
			{
				return i;
			}
		}
		
		return -1;
	}
	
	static boolean isMatch(PA_Task task, Class<? extends PA_Task> taskClass, BleManager mngr_nullable, BleDevice device_nullable)
	{
		if( task == null )  return false;
		
		if( task.getClass() == taskClass )
		{
			if( mngr_nullable == null )
			{
				if( device_nullable == null || (device_nullable != null && device_nullable.equals(task.getDevice())) )
				{
					return true;
				}
			}
			else
			{
				if( mngr_nullable == task.getManager() )
				{
					return true;
				}
			}
		}
		
		return false;
	}
}
