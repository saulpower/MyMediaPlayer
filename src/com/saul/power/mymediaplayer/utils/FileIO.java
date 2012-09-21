package com.saul.power.mymediaplayer.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.Environment;
import android.util.Log;

public class FileIO {
	
	private static final String TAG = "FileIO";
	
	public static final String STORAGE_PATH = "/videos/";

	public static File getExternalDirectory() {

		String dir = Environment.getExternalStorageDirectory().getAbsolutePath();
		File dataDir = new File(dir + STORAGE_PATH);
		
		if (!dataDir.exists())
			dataDir.mkdirs();
		
		return  dataDir;
	}

	public static String loadRemoteData(String link, String filename) {
		
		File outputFile;
		
		try {
            
			Log.i(TAG, "Downloading File: " + link);
			
			File dataDir = getExternalDirectory();
			
			// Deletes the file if it exists
			outputFile = new File(dataDir, filename);
			outputFile.delete();
			
			//this will be used to write the downloaded data into the file we created
	        FileOutputStream fileOutput = new FileOutputStream(outputFile);
			
	        URL url = new URL(link);
	        
	        //create the new connection
	        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
	        
	        //set up some things on the connection
	        urlConnection.setRequestMethod("GET");
	        
	        //and connect!
	        urlConnection.connect();
	        
	        //this will be used in reading the data from the internet
	        InputStream inputStream = urlConnection.getInputStream();

	        //create a buffer...
	        byte[] buffer = new byte[1024];
	        int bufferLength = 0; //used to store a temporary size of the buffer
	        
	        //now, read through the input buffer and write the contents to the file
	        while ((bufferLength = inputStream.read(buffer)) > 0) {
	        	
                //add the data in the buffer to the file in the file output stream (the file on the sd card
                fileOutput.write(buffer, 0, bufferLength);
	        }
	        
	        //close the output stream when done
	        fileOutput.close();

		} catch (Exception e) {
	        e.printStackTrace();
	        return "";
		}
		
		return outputFile.getAbsolutePath();
	}

	public static String saveToDisc(byte[] data, String filename) {
		
		File dataDir = getExternalDirectory();
		File outputFile = new File(dataDir, filename);
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(outputFile);
			fos.write(data);
		} catch (Exception e) {} finally {
			try {
				if (fos != null) {
					fos.flush();
					fos.close();
				}
			} catch (IOException e1) {}
		}
		return outputFile.getAbsolutePath();
	}

	public static byte[] retrieveFromDisc(String filename) {
		
		File file = new File(getExternalDirectory(), filename);
		
		if (!file.exists())
			return null;
			
		return retrieveFromDisc(file);
	}

	private static byte[] retrieveFromDisc(File path) {
		
		byte[] data = new byte[0];
		InputStream stream = null;
		try {
			stream = getStream(path);
			data = getBytesFromInputStream(stream);
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		} finally {
			try {
				if (stream != null)
					stream.close();
			} catch (Exception e) {}
		}
		return data;
	}

	private static InputStream getStream(File path) throws FileNotFoundException {
		return new FileInputStream(path);
	}

	private static byte[] getBytesFromInputStream(InputStream is) throws IOException {
		
		long length = is.available();
		byte[] bytes = new byte[(int) length];
		int offset = 0;
		int numRead = 0;
		
		while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
			offset += numRead;
		}
		
		if (offset < bytes.length) { throw new IOException("Could not completely read file "); }
		is.close();
		
		return bytes;
	}
}
