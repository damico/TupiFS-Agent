package org.jdamico.tupifs.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ManageProperties {
	private static ManageProperties INSTANCE;

	public static ManageProperties getInstance() {
		if (INSTANCE == null)	INSTANCE = new ManageProperties();
		return INSTANCE;
	}
	private ManageProperties(){}

	public String read(String prop) throws TupiFsException {
		// Read properties file.
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(Constants.PROPERTIES_PATH));
		} catch (IOException e) {
			throw new TupiFsException(e);
		}
		
		return properties.getProperty(prop);
	}

	public String read(String propPath, String prop) throws TupiFsException {
		// Read properties file.
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(propPath));
		} catch (IOException e) {
			throw new TupiFsException(e);
		}
		return properties.getProperty(prop);
	}

	public Properties setPropFile(String filename, String date, int type) throws TupiFsException{
		Properties properties = new Properties();
		try { 
			properties.setProperty("date", date);
			properties.setProperty("type", String.valueOf(type));
			properties.store(new FileOutputStream(filename), null);
		} catch (IOException e) {throw new TupiFsException(e); } 
		return properties;
	}
}
