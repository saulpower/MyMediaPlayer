package com.saul.power.mymediaplayer.utils;

import java.io.File;

public class CacheUtils {

	public static String cacheResource(String url, boolean refresh) {
		
		String filename = null;
		
		if (!url.equals("")) {
			
			filename = "resource_" +  Math.abs(url.hashCode()) + getExtension(url);

			File dataDir = FileIO.getExternalDirectory();
			File file = new File(dataDir, filename);
			
			if (!file.exists() || refresh)
				filename = FileIO.loadRemoteData(url, filename);
			else
				filename = file.getAbsolutePath();
		}
		
		return filename;
	}
	
	private static String getExtension(String url) {
		
		String[] parts = url.split("[.]");
		
		if (parts.length > 1 && parts[(parts.length - 1)].length() <= 4)
			return "." + parts[(parts.length - 1)];
		
		return "";
	}
	
}
