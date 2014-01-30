package com.bsb.hike.filetransfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.MediaStore.MediaColumns;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.R;
import com.bsb.hike.HikeConstants.FTResult;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.filetransfer.FileTransferBase.FTState;
import com.bsb.hike.filetransfer.FileTransferManager.NetworkType;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.HikeFile;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.utils.AccountUtils;
import com.bsb.hike.utils.FileTransferCancelledException;
import com.bsb.hike.utils.Utils;

public class UploadFileTask extends FileTransferBase
{
	private String X_SESSION_ID;

	private Uri picasaUri = null;

	private URL mUrl;

	private String fileType;

	private String token;

	private String uId;

	private String msisdn;

	private boolean isRecipientOnhike;

	private boolean isRecording;

	private File selectedFile = null;

	private long recordingDuration = -1;

	private boolean isForwardMsg;

	private FutureTask<FTResult> futureTask;

	private int num = 0;

	private static String BOUNDARY = "----------V2ymHFg03ehbqgZCaKO6jy";

//	protected UploadFileTask(Handler handler, ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap, Context ctx, String token, String uId, String msisdn, File sourceFile,
//			String fileType, HikeFileType hikeFileType, boolean isRecording, boolean isForwardMsg, boolean isRecipientOnHike, long recordingDuration)
//	{
//		super(handler, fileTaskMap, ctx, sourceFile, -1, hikeFileType);
//		this.token = token;
//		this.uId = uId;
//		this.msisdn = msisdn;
//		this.fileType = fileType;
//		this.isRecipientOnhike = isRecipientOnHike;
//		this.recordingDuration = recordingDuration;
//		this.isRecording = isRecording;
//		this.isForwardMsg = isForwardMsg;
//		this.isRecipientOnhike = isRecipientOnHike;
//	}

	protected UploadFileTask(Handler handler, ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap, Context ctx, String token, String uId, Object convMessage,
			boolean isRecipientOnHike)
	{
		super(handler, fileTaskMap, ctx, null, -1, null);
		this.token = token;
		this.uId = uId;
		this.isRecipientOnhike = isRecipientOnHike;
		userContext = convMessage;
	}
	
	protected UploadFileTask(Handler handler, ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap, Context ctx, String token, String uId, Uri picasaUri, Object convMessage,
			boolean isRecipientOnHike)
	{
		super(handler, fileTaskMap, ctx, null, -1, null);
		this.token = token;
		this.uId = uId;
		this.picasaUri = picasaUri;
		this.isRecipientOnhike = isRecipientOnHike;
		userContext = convMessage;
	}

//	protected UploadFileTask(Handler handler, ConcurrentHashMap<Long, FutureTask<FTResult>> fileTaskMap, Context ctx, String token, String uId, Uri picasaUri,
//			HikeFileType hikeFileType, String msisdn, boolean isRecipientOnHike)
//	{
//		super(handler, fileTaskMap, ctx, null, -1, null);
//		this.token = token;
//		this.uId = uId;
//		this.picasaUri = picasaUri;
//		this.hikeFileType = hikeFileType;
//		this.msisdn = msisdn;
//		this.isRecipientOnhike = isRecipientOnHike;
//	}

	protected void setFutureTask(FutureTask<FTResult> fuTask)
	{
		futureTask = fuTask;
	}

	/**
	 * This function do the initial steps for uploading ..... 1. Create Thumbnail 2. Create ConvMessage 3. Create copy of file to upload (if required)
	 * 
	 * Note : All these steps are done if and only if required else this function will simply return
	 * 
	 * @throws Exception
	 */
	private void initFileUpload() throws FileTransferCancelledException, Exception
	{	
		_state = FTState.IN_PROGRESS;
		msgId = ((ConvMessage) userContext).getMsgID();
		fileTaskMap.put(msgId, futureTask);
		HikeFile hikeFile = ((ConvMessage) userContext).getMetadata().getHikeFiles().get(0);
		hikeFileType = hikeFile.getHikeFileType();
		
		selectedFile = new File(hikeFile.getFilePath());
		String fileName = selectedFile.getName();
		if (hikeFile.getFilePath() == null)
		{
			throw new FileNotFoundException("File is not accessible. SDCard unmount");
		}
		if(picasaUri == null)
		{
			if(hikeFile.getSourceFilePath() == null)
			{
				Log.d("filepath: ",selectedFile.getPath());
				Log.d("fillepath: ",Utils.getFileParent(hikeFileType));
			}
			else
			{
				mFile = new File(hikeFile.getSourceFilePath());
				if(mFile.getPath().startsWith(Utils.getFileParent(hikeFileType)))
				{
					selectedFile = mFile;
				}
				else
				{
					selectedFile = Utils.getOutputMediaFile(hikeFileType, fileName);
					if (selectedFile == null)
						throw new Exception(FileTransferManager.READ_FAIL);
	
					if (!selectedFile.exists())
					{
						// Saving the file to hike local folder
						if (!Utils.copyFile(mFile.getPath(), selectedFile.getPath(), hikeFileType))
						{
							Log.d(getClass().getSimpleName(), "throwing copy file exception");
							throw new Exception(FileTransferManager.READ_FAIL);
						}
					}
				}
				hikeFile.removeSourceFile();
//				Bitmap thumbnail = null;
//				String thumbnailString = null;
//				if (hikeFileType == HikeFileType.IMAGE)
//				{
//					thumbnail = Utils.scaleDownImage(selectedFile.getPath(), HikeConstants.MAX_DIMENSION_THUMBNAIL_PX, false);
//				}
//				else if (hikeFileType == HikeFileType.VIDEO)
//				{
//					thumbnail = ThumbnailUtils.createVideoThumbnail(selectedFile.getPath(), MediaStore.Images.Thumbnails.MICRO_KIND);
//				}
//				if (thumbnail != null)
//				{
//					thumbnailString = Base64.encodeToString(Utils.bitmapToBytes(thumbnail, Bitmap.CompressFormat.JPEG, 75), Base64.DEFAULT);
//				}
//				JSONObject metadata = getFileTransferMetadata(fileName, fileType, hikeFileType, thumbnailString, thumbnail);
//				((ConvMessage) userContext).setMetadata(metadata);
//				HikeConversationsDatabase.getInstance().updateMessageMetadata(((ConvMessage) userContext).getMsgID(), ((ConvMessage) userContext).getMetadata());
			}
		}
		else
		{
			Utils.downloadAndSaveFile(context, selectedFile, picasaUri);
			Bitmap thumbnail = null;
			String thumbnailString = null;
			if (hikeFileType == HikeFileType.IMAGE)
			{
				thumbnail = Utils.scaleDownImage(selectedFile.getPath(), HikeConstants.MAX_DIMENSION_THUMBNAIL_PX, false);
			}
			else if (hikeFileType == HikeFileType.VIDEO)
			{
				thumbnail = ThumbnailUtils.createVideoThumbnail(selectedFile.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
			}
			if (thumbnail != null)
			{
				thumbnailString = Base64.encodeToString(Utils.bitmapToBytes(thumbnail, Bitmap.CompressFormat.JPEG, 75), Base64.DEFAULT);
			}
			else
			{
				throw new Exception("Network error");
			}
			JSONObject metadata = getFileTransferMetadata(fileName, fileType, hikeFileType, thumbnailString, thumbnail);
			((ConvMessage) userContext).setMetadata(metadata);
			HikeConversationsDatabase.getInstance().updateMessageMetadata(((ConvMessage) userContext).getMsgID(), ((ConvMessage) userContext).getMetadata());
		}
		
		fileName = hikeFile.getFileName();
		fileType = hikeFile.getFileTypeString();
		hikeFileType = hikeFile.getHikeFileType();
		
		stateFile = new File(FileTransferManager.getInstance(context).getHikeTempDir(), fileName + ".bin." + ((ConvMessage) userContext).getMsgID());
		Log.d(getClass().getSimpleName(), "Upload state bin file :: " + fileName + ".bin." + ((ConvMessage) userContext).getMsgID());
	}

	private JSONObject getFileTransferMetadata(String fileName, String fileType, HikeFileType hikeFileType, String thumbnailString, Bitmap thumbnail) throws JSONException
	{
		JSONArray files = new JSONArray();
		files.put(new HikeFile(fileName, TextUtils.isEmpty(fileType) ? HikeFileType.toString(hikeFileType) : fileType, thumbnailString, thumbnail, recordingDuration).serialize());
		JSONObject metadata = new JSONObject();
		metadata.put(HikeConstants.FILES, files);
		return metadata;
	}

	@Override
	public FTResult call()
	{
		try
		{
			initFileUpload();
		}
		catch (FileTransferCancelledException e)
		{
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (FileNotFoundException e)
		{
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.CARD_UNMOUNT;
		}
		catch (Exception e)
		{
			if (e != null)
			{
				Log.e(getClass().getSimpleName(), "Exception", e);
				if (FileTransferManager.READ_FAIL.equals(e.getMessage()))
					return FTResult.READ_FAIL;
				else if(FileTransferManager.UNABLE_TO_DOWNLOAD.equals(e.getMessage()))
					return FTResult.DOWNLOAD_FAILED;
			}
		}

		try
		{
			if (_state == FTState.CANCELLED)
				return FTResult.CANCELLED;

			_state = FTState.IN_PROGRESS;
			boolean fileWasAlreadyUploaded = true;

			// If we don't have a file key, that means we haven't uploaded the
			// file to the server yet
			if (TextUtils.isEmpty(fileKey))
			{
				fileWasAlreadyUploaded = false;

				JSONObject response = uploadFile(selectedFile); // <<----- this is the main upload function where upload to server is done

				if (_state == FTState.CANCELLED)
					return FTResult.CANCELLED;
				else if (_state == FTState.PAUSED)
					return FTResult.PAUSED;
				else if (response == null)
					return FTResult.UPLOAD_FAILED;
				JSONObject fileJSON = response.getJSONObject(HikeConstants.DATA_2);
				fileKey = fileJSON.optString(HikeConstants.FILE_KEY);
				fileType = fileJSON.optString(HikeConstants.CONTENT_TYPE);
				String md5Hash = fileJSON.optString("md5_original");
				Log.d(getClass().getSimpleName(), "Server md5 : " + md5Hash);
				if (md5Hash != null)
				{
					String file_md5Hash = Utils.fileToMD5(selectedFile.getPath());
					Log.d(getClass().getSimpleName(), "Phone's md5 : " + file_md5Hash);
					if (!md5Hash.equals(file_md5Hash))
					{
						Log.d(getClass().getSimpleName(), "The md5's are not equal...Deleting the files...");
						deleteStateFile();
						return FTResult.FAILED_UNRECOVERABLE;
					}

				}
				else
				{
					deleteStateFile();
					return FTResult.FAILED_UNRECOVERABLE;
				}
			}

			JSONObject metadata = new JSONObject();
			JSONArray filesArray = new JSONArray();

			HikeFile hikeFile = ((ConvMessage) userContext).getMetadata().getHikeFiles().get(0);
			hikeFile.setFileKey(fileKey);
			hikeFile.setFileTypeString(fileType);

			filesArray.put(hikeFile.serialize());
			metadata.put(HikeConstants.FILES, filesArray);

			((ConvMessage) userContext).setMetadata(metadata);

			// If the file was just uploaded to the servers, we want to publish
			// this event
			if (!fileWasAlreadyUploaded)
			{
				HikeMessengerApp.getPubSub().publish(HikePubSub.UPLOAD_FINISHED, ((ConvMessage) userContext));
			}

			Utils.addFileName(hikeFile.getFileName(), hikeFile.getFileKey());
			HikeMessengerApp.getPubSub().publish(HikePubSub.MESSAGE_SENT, ((ConvMessage) userContext));

		}
		catch (MalformedURLException e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (FileNotFoundException e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (ClientProtocolException e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (IOException e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (JSONException e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		catch (Exception e)
		{
			error();
			Log.e(getClass().getSimpleName(), "Exception", e);
			return FTResult.UPLOAD_FAILED;
		}
		return FTResult.SUCCESS;
	}

	private JSONObject uploadFile(File sourceFile) throws MalformedURLException, FileNotFoundException, IOException, JSONException, ClientProtocolException, Exception
	{
		int mStart = 0;
		JSONObject responseJson = null;
		FileSavedState fst = FileTransferManager.getInstance(context).getUploadFileState(sourceFile, msgId);
		setFileTotalSize((int) sourceFile.length());
		//Bug Fix: 13029
		setBytesTransferred(fst.getTransferredSize());
		long temp = _bytesTransferred;
		temp *= 100;
		temp /= _totalSize;
		progressPercentage = (int) temp;
		// represents this file is either not started or unrecovered error has happened
		Log.d(getClass().getSimpleName(), "Starting Upload from state : " + fst.getFTState().toString());
		if (fst.getFTState().equals(FTState.NOT_STARTED))
		{
			// here as we are starting new upload, we have to create the new session id
			X_SESSION_ID = UUID.randomUUID().toString();
			Log.d(getClass().getSimpleName(), "SESSION_ID: " + X_SESSION_ID);
		}
		else if (fst.getFTState().equals(FTState.PAUSED) || fst.getFTState().equals(FTState.ERROR))
		{
			/* 
			 * In case user paused the transfer during the last chunk.
			 * The Upload was completed and the response from server was stored with the state file.
			 * So when resumed, the response is read from state file.
			 * If this is not null the response is returned.
			 */
			if(fst.getResponseJson() != null)
			{
				_state = FTState.COMPLETED;
				deleteStateFile();
				
				responseJson = fst.getResponseJson();
				return responseJson;
			}
			X_SESSION_ID = fst.getSessionId();
			Log.d(getClass().getSimpleName(), "SESSION_ID: " + X_SESSION_ID);
			mStart = AccountUtils.getBytesUploaded(String.valueOf(X_SESSION_ID));
		}
		// @GM setting transferred bytes if there are any
		setBytesTransferred(mStart);
		mUrl = new URL(AccountUtils.fileTransferUploadBase + "/user/pft/");
		RandomAccessFile raf = new RandomAccessFile(sourceFile, "r");
		raf.seek(mStart);
		long length = sourceFile.length();
		NetworkType networkType = FileTransferManager.getInstance(context).getNetworkType();
		int chunkSize = networkType.getMinChunkSize();
		int start = mStart;
		int end = (int) length;
		if (end > (start + chunkSize))
			end = start + chunkSize;
		end--;
		byte[] fileBytes = null;
		/*
		 * The following loops goes on till the end byte to read reaches the length
		 * or we receive a json response from the server
		 */
		//
		while(end < length && responseJson == null)
		{
			if (_state != FTState.IN_PROGRESS) // this is to check if user has PAUSED or cancelled the upload
				break;
			
			fileBytes = new byte[end - start + 1]; //Byte Size to read from the file
			//In case of success following flag is set high to reset retry logic and update UI
			boolean resetAndUpdate = false;
			if (raf.read(fileBytes) == -1)
			{
				raf.close();
				throw new IOException("Exception in partial read. files ended");
			}
			String contentRange = "bytes " + start + "-" + end + "/" + length;
			String responseString = send(contentRange, fileBytes);
			Log.d(getClass().getSimpleName(), "JSON response : " + responseString);
			/*
			 * When end byte uploaded is the last byte of the file and server send response
			 * i.e. upload successfully completed
			 */
			 
			if(end == (length-1) && responseString != null)
			{
				responseJson = new JSONObject(responseString);
				resetAndUpdate = true;	//To update UI
			}
			// When upload is not complete
			else
			{
				// In case there is error uploading this chunk
				if(responseString == null)
				{
					/*
					 * If retry attempt is to be made.
					 * The chunk size is reduced for next attempt
					 */
					 
					if(shouldRetry())
					{
						raf.seek(start);
						if(networkType == FileTransferManager.getInstance(context).getNetworkType())
						{
							chunkSize/=2;
						}
						else
						{
							networkType = FileTransferManager.getInstance(context).getNetworkType();
							chunkSize = networkType.getMinChunkSize();
						}
					}
					else
					{
						raf.close();
						throw new IOException("Exception in partial upload. response null");
					}
					
				}
				// When the chunk uploaded successfully
				else
				{
					start += chunkSize;
					// ChunkSize is increased within the limits
					chunkSize *= 2;
					if(chunkSize > networkType.getMaxChunkSize())
						chunkSize = networkType.getMaxChunkSize();
					else if (chunkSize < networkType.getMinChunkSize())
						chunkSize = networkType.getMinChunkSize();
					
					resetAndUpdate = true;	// To reset retry logic and update UI
				}
				/*
				 * This chunk size should ideally be no more than 1/8 of the total memory available.
				 */
				try
				{
					int maxMemory = (int) Runtime.getRuntime().maxMemory();
					if( chunkSize > (maxMemory / 8) )
					chunkSize = maxMemory / 8 ;
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
				end = (int) length;
				if (end > (start + chunkSize))
					end = start + chunkSize;
				end--;
			}
			/*
			 * Resetting reconnect logic
			 * Updating UI
			 */
			if(resetAndUpdate)
			{
				retry = true;
				reconnectTime = 0;
				retryAttempts = 0;
				incrementBytesTransferred(fileBytes.length);
				temp = _bytesTransferred;
				temp *= 100;
				temp /= _totalSize;
				progressPercentage = (int) temp;
				LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(HikePubSub.FILE_TRANSFER_PROGRESS_UPDATED));
				//showButton();
			}
			fileBytes = null;
		}
		switch (_state)
		{
		case CANCELLED:
			Log.d(getClass().getSimpleName(), "FT Cancelled");
			deleteStateFile();
			break;
		case IN_PROGRESS:
			Log.d(getClass().getSimpleName(), "FT Completed");
			_state = FTState.COMPLETED;
			deleteStateFile();
			break;
		case PAUSING:
			_state = FTState.PAUSED;
			Log.d(getClass().getSimpleName(), "FT PAUSED");
			// In case upload was complete response JSON is to be saved not the Session_ID
			if(responseJson != null)
				saveFileState(responseJson);
			else
				saveFileState(X_SESSION_ID);
			break;
		default:
			break;
		}
		try
		{
			// we don't want to screw up result even if inputstream is not closed
			raf.close();
		}
		catch (IOException e)
		{

		}
		return responseJson;
	}

	String getBoundaryMessage(String contentRange)
	{
		StringBuffer res = new StringBuffer("--").append(BOUNDARY).append("\r\n");
		res.append("Content-Disposition: form-data; name=\"");
		res.append("Cookie").append("\"\r\n").append("\r\n");
		res.append(uId).append("\r\n").append("--").append(BOUNDARY).append("\r\n");
		res.append("Content-Disposition: form-data; name=\"");
		res.append("X-CONTENT-RANGE").append("\"\r\n").append("\r\n");
		res.append(contentRange).append("\r\n").append("--").append(BOUNDARY).append("\r\n");
		res.append("Content-Disposition: form-data; name=\"");
		res.append("X-SESSION-ID").append("\"\r\n").append("\r\n");
		res.append(X_SESSION_ID).append("\r\n").append("--").append(BOUNDARY).append("\r\n");
		res.append("Content-Disposition: form-data; name=\"").append("file").append("\"; filename=\"").append(selectedFile.getName()).append("\"\r\n").append("Content-Type: ")
				.append(TextUtils.isEmpty(fileType) ? "" : fileType).append("\r\n\r\n");
		return res.toString();
	}

	/*
	 * this function was created to notify the UI
	 * but is not required for now. Not deleted if required again
	 */
	private boolean shouldSendProgress()
	{
		int x = progressPercentage / 10;
		if (x < num)
			return false;
		// @GM 'num++' will create a problem in future if with decide to increase "BUFFER_SIZE"(which we will)
		// num++;
		num = x + 1;
		return true;
	}

//	private void showButton()
//	{
//		Intent intent = new Intent(HikePubSub.RESUME_BUTTON_UPDATED);
//		intent.putExtra("msgId", msgId);
//		LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
//	}
	private String send(String contentRange, byte[] fileBytes)
	{
		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(mUrl.toString());
		String res = null;
		try
		{	
			post.addHeader("Connection", "Keep-Alive");
			post.addHeader("Content-Name", selectedFile.getName());
			post.addHeader("X-Thumbnail-Required", "0");
			post.addHeader("X-SESSION-ID", X_SESSION_ID);
			post.addHeader("X-CONTENT-RANGE", contentRange);
			post.addHeader("Cookie", "user=" + token + ";uid=" + uId);
			post.setHeader("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

			byte[] postBytes = getPostBytes(contentRange, fileBytes);
			post.setEntity(new ByteArrayEntity(postBytes));
			Log.d(getClass().getSimpleName(), "Before Thread Details : " + Thread.currentThread().toString() + "Time : " + System.currentTimeMillis() / 1000);
			HttpResponse response = client.execute(post);
			Log.d(getClass().getSimpleName(), "After Thread Details : " + Thread.currentThread().toString() + "Time : " + System.currentTimeMillis() / 1000);
		    BufferedReader r = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
			StringBuilder total = new StringBuilder();
			String line;
			while ((line = r.readLine()) != null)
			{
				total.append(line);
			}
			res = total.toString();
			retry = false; // if success don't retry again till next time
		}
		catch (Exception e)
		{
			Log.d(getClass().getSimpleName(), "Caught Exception: " + e.getMessage());
			if (e.getMessage() != null && (e.getMessage().contains(NETWORK_ERROR_1) || e.getMessage().contains(NETWORK_ERROR_2)
					|| e.getMessage().contains(NETWORK_ERROR_3)))
			{
				Log.e(getClass().getSimpleName(), "Exception while uploading : " + e.getMessage());
				// we should retry if failed due to network
			}
			else
			{
				error();
				res = null;
				retry = false;
			}
		}

		return res;
	}

	private FTResult closeStreams(ByteArrayOutputStream bos, InputStream is, HttpURLConnection hc)
	{
		try
		{
			if (bos != null)
				bos.close();
		}
		catch (Exception e2)
		{
			e2.printStackTrace();
		}
		try
		{
			if (is != null)
				is.close();
		}
		catch (Exception e2)
		{
			e2.printStackTrace();
		}
		try
		{
			if (hc != null)
				hc.disconnect();
		}
		catch (Exception e2)
		{
			e2.printStackTrace();
		}
		return FTResult.SUCCESS;
	}

	private byte[] getPostBytes(String contentRange, byte[] fileBytes)
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] postBytes = null;
		try
		{
			bos.write(getBoundaryMessage(contentRange).getBytes());
			bos.write(fileBytes);
			bos.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes());
			postBytes = bos.toByteArray();
		}
		catch (IOException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			if (bos != null)
				try
				{
					bos.close();
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return postBytes;
	}

	private void error()
	{
		_state = FTState.ERROR;
		saveFileState(X_SESSION_ID);
	}

	public void postExecute(FTResult result)
	{
		Log.d(getClass().getSimpleName(), "PostExecute--> Thread Details : " + Thread.currentThread().toString() + "Time : " + System.currentTimeMillis() / 1000);
		if (userContext != null)
		{
			FileTransferManager.getInstance(context).removeTask(((ConvMessage) userContext).getMsgID());
			if (result == FTResult.SUCCESS)
			{
				((ConvMessage) userContext).setTimestamp(System.currentTimeMillis() / 1000);
			}
			LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(HikePubSub.FILE_TRANSFER_PROGRESS_UPDATED));
		}

		else if (result != FTResult.SUCCESS && result != FTResult.PAUSED)
		{
			final int errorStringId = result == FTResult.READ_FAIL ? R.string.unable_to_read : result == FTResult.FAILED_UNRECOVERABLE ? R.string.download_failed_fatal
					: result == FTResult.CARD_UNMOUNT ? R.string.card_unmount : result == FTResult.DOWNLOAD_FAILED ? R.string.download_failed : R.string.upload_failed;

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					Toast.makeText(context, errorStringId, Toast.LENGTH_SHORT).show();
				}
			});
		}
		if (selectedFile != null && hikeFileType != HikeFileType.AUDIO_RECORDING)
		{
			context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(selectedFile)));
		}
		//showButton();
	}
}
