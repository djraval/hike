/**
 * 
 */
package com.bsb.hike.modules.contactmgr;

import java.util.List;

import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.modules.iface.ITransientCache;

/**
 * @author Gautam
 * 
 */
public class ContactManager implements ITransientCache
{
	// This should always be present so making it loading on class loading itself
	private volatile static ContactManager _instance = new ContactManager();

	private ContactsCache cache = new ContactsCache();

	private ContactManager()
	{
		cache = new ContactsCache();
		loadPersistenceCache();
	}

	/**
	 * This method loads the persistence memory
	 */
	private void loadPersistenceCache()
	{
		cache.loadPersistenceMemory();
	}

	/**
	 * This method puts the contactInfo object list in persistence memory
	 * @param contacts
	 */
	public List<ContactInfo> loadPersistenceCache(String msisdnsDB)
	{
		return cache.loadPersistenceMemory(msisdnsDB);
	}
	
	public static ContactManager getInstance()
	{
		return _instance;
	}

	/**
	 * This method should load the transient memory at app launch event
	 */
	@Override
	public void load()
	{
		cache.loadTransientMem();
	}

	/**
	 * This method should unload the transient memory at app launch event
	 */
	@Override
	public void unload()
	{
		cache.clearTransientMemory();
	}

	/**
	 * This function will return name or null for a particular msisdn
	 * 
	 * @param msisdn
	 * @return
	 */
	public ContactInfo getContact(String msisdn)
	{
		ContactInfo c = null;
		c = cache.getContact(msisdn);
		return c;
	}
}