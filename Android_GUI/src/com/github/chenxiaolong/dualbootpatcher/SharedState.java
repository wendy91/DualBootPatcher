package com.github.chenxiaolong.dualbootpatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.Arrays;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Button;

public class SharedState {
	public static WeakReference<MainActivity> mActivity = null;
	
	public static final int REQUEST_WINDOW_HANDLE = 1;
	public static final int REQUEST_FILE = 1234;
	public static String mHandle;
	
	// Buttons
	public static boolean mPatchFileVisible = false; 
	
	// File
	public static Uri zipFile = null;
	
	public static String mPatcherFileBase = "DualBootPatcherAndroid-";
	public static String mPatcherFileName = "";
	public static String mPatcherFileVer = "";
	//public static String mPatcherFileExt = ".zip";
	public static String mPatcherFileExt = ".tar.xz";
	
	public static BufferedReader stdout = null;
	public static BufferedReader stderr = null;
	
	// Dialogs
    public static ProgressDialog mProgressDialog = null;
    public static boolean mProgressDialogVisible = false;
    public static String mProgressDialogText = "";
    public static String mProgressDialogTitle = "";
	
	public static AlertDialog.Builder mConfirmDialogBuilder = null;
	public static AlertDialog mConfirmDialog = null;
	public static boolean mConfirmDialogVisible = false;
	public static String mConfirmDialogTitle = "";
	public static String mConfirmDialogText = "";
	public static DialogInterface.OnClickListener mConfirmDialogNegative = null;
	public static DialogInterface.OnClickListener mConfirmDialogPositive = null;
	public static String mConfirmDialogNegativeText = "";
	public static String mConfirmDialogPositiveText = "";
	
	public static class ConfirmDialogNegative implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			mConfirmDialogVisible = false;
			SharedState.mConfirmDialog.cancel();
		}
	}
	
	public static class ConfirmDialogPositive implements DialogInterface.OnClickListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			mConfirmDialogVisible = false;
			((Button)mActivity.get().findViewById(R.id.choose_file)).setEnabled(false);
			
			/* Show progress dialog */
	        mProgressDialogTitle = mActivity.get().getString(R.string.progress_title_patching_files);
	        mProgressDialogText = mActivity.get().getString(R.string.progress_text);
	        mProgressDialog.setTitle(mProgressDialogTitle);
	        mProgressDialog.setMessage(mProgressDialogText);
	        mProgressDialog.show();
	        mProgressDialogVisible = true;
			
			new Thread() {
				public void run() {
					String command[];
					
					/* Extract the patcher zip if it isn't already */
					File target = new File(mActivity.get().getCacheDir() + "/" + mPatcherFileName);
					File targetDir = new File(mActivity.get().getFilesDir() + "/" +
							mPatcherFileBase + mPatcherFileVer);
					
					/* Remove temporary files in case the script crashes and
					 * doesn't clean itself up properly */
					mHandler.obtainMessage(
							EVENT_UPDATE_PROGRESS_TITLE,
							mActivity.get().getString(R.string.progress_title_removing_temp))
							.sendToTarget();
					
					command = new String[] {
							"sh", "-c",
							"rm -rf " + mActivity.get().getFilesDir() + "/*/tmp*"
					};
					
					run_command(command, null, null);
					
					if (!targetDir.exists()) {
						mHandler.obtainMessage(
								EVENT_UPDATE_PROGRESS_TITLE,
								mActivity.get().getString(R.string.progress_title_updating_files))
								.sendToTarget();
						
						/* Remove all previous files */
						command = new String[] {
								"sh", "-c",
								"rm -rf " + mActivity.get().getFilesDir() + "/*"
						};
						
						run_command(command, null, null);
						
						try {
							InputStream i = mActivity.get().getAssets().open(mPatcherFileName);
							FileOutputStream o = new FileOutputStream(target);
							
							//byte[] buffer = new byte[4096];
							//int length;
							//while ((length = i.read(buffer)) > 0) {
					        //    o.write(buffer, 0, length);
					        //}
							
							/* The files are small enough to just read all of it into memory */
							int length = i.available();
							byte[] buffer = new byte[length];
							i.read(buffer);
							o.write(buffer);
							
					        o.flush();
					        o.close();
					        i.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						
						command = new String[] {
								"sh", "-c",
								//"unzip -o " + target.getPath()
								"tar Jxvf " + target.getPath()
						};
						
						run_command(command, null, mActivity.get().getFilesDir());
						
						command = new String[] {
								"chmod", "755",
								"pythonportable/bin/python"
						};
						
						run_command(command, null, targetDir);
					}
					
					mHandler.obtainMessage(
							EVENT_UPDATE_PROGRESS_TITLE,
							mActivity.get().getString(R.string.progress_title_patching_files))
							.sendToTarget();
					
					command = new String[] {
							"pythonportable/bin/python", "-B",
							"scripts/patchfile.py", SharedState.zipFile.getPath()
					};
					
					int exit_code = run_command(command,
							new String[] { "LD_LIBRARY_PATH=pythonportable/lib",
							               "PYTHONUNBUFFERED=true" },
							targetDir);
					
					mHandler.obtainMessage(EVENT_CLOSE_PROGRESS_DIALOG).sendToTarget();
					mHandler.obtainMessage(EVENT_ENABLE_CHOOSE_FILE_BUTTON).sendToTarget();
					
					if (exit_code == 0) {
						// TODO: Don't hardcode this
						String newFile = zipFile.getPath().replace(".zip", "_dualboot.zip");
						mHandler.obtainMessage(EVENT_SHOW_COMPLETION_DIALOG, newFile).sendToTarget();
					}
					else {
						mHandler.obtainMessage(EVENT_SHOW_FAILED_DIALOG, zipFile.getPath()).sendToTarget();
					}
				}
			}.start();
		}
	}
	
	
	public static final int EVENT_ENABLE_CHOOSE_FILE_BUTTON = 1;
	public static final int EVENT_CLOSE_PROGRESS_DIALOG = 2;
	public static final int EVENT_UPDATE_PROGRESS_MSG = 3;
	public static final int EVENT_UPDATE_PROGRESS_TITLE = 4;
	public static final int EVENT_SHOW_COMPLETION_DIALOG = 5;
	public static final int EVENT_SHOW_FAILED_DIALOG = 6;
	
	public static final Handler mHandler = new Handler(Looper.getMainLooper()) {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case EVENT_UPDATE_PROGRESS_MSG:
				mProgressDialogText = (String)msg.obj;
				mProgressDialog.setMessage(mProgressDialogText);
				break;
				
			case EVENT_UPDATE_PROGRESS_TITLE:
				mProgressDialogTitle = (String)msg.obj;
				mProgressDialog.setTitle(mProgressDialogTitle);
				break;
				
			case EVENT_SHOW_COMPLETION_DIALOG:
				mConfirmDialogTitle = mActivity.get().getString(
						R.string.dialog_patch_zip_title_success);
				mConfirmDialogText = mActivity.get().getString(
						R.string.dialog_text_new_file) + (String)msg.obj;
				
				mConfirmDialogBuilder.setTitle(mConfirmDialogTitle);
				mConfirmDialogBuilder.setMessage(mConfirmDialogText);
				
				mConfirmDialogNegative = null;
    			mConfirmDialogPositive = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mConfirmDialogVisible = false;
						mConfirmDialog.dismiss();
					}
				};
				mConfirmDialogNegativeText = null;
    			mConfirmDialogPositiveText =
    					mActivity.get().getString(R.string.dialog_finish);
				
        		mConfirmDialogBuilder.setNegativeButton(
        				mConfirmDialogNegativeText,
        				mConfirmDialogNegative);
        		mConfirmDialogBuilder.setPositiveButton(
        				mConfirmDialogPositiveText,
        				mConfirmDialogPositive);
        		
        		mConfirmDialog = mConfirmDialogBuilder.create();
        		
        		mConfirmDialog.show();
        		mConfirmDialogVisible = true;
        		break;
        		
			case EVENT_SHOW_FAILED_DIALOG:
				mConfirmDialogTitle = mActivity.get().getString(
						R.string.dialog_patch_zip_title_failed);
				mConfirmDialogText = mActivity.get().getString(
						R.string.dialog_text_file) + (String)msg.obj;
				
				mConfirmDialogBuilder.setTitle(mConfirmDialogTitle);
				mConfirmDialogBuilder.setMessage(mConfirmDialogText);
				
				mConfirmDialogNegative = null;
    			mConfirmDialogPositive = new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mConfirmDialogVisible = false;
						mConfirmDialog.dismiss();
					}
				};
				mConfirmDialogNegativeText = null;
    			mConfirmDialogPositiveText =
    					mActivity.get().getString(R.string.dialog_finish);
				
        		mConfirmDialogBuilder.setNegativeButton(
        				mConfirmDialogNegativeText,
        				mConfirmDialogNegative);
        		mConfirmDialogBuilder.setPositiveButton(
        				mConfirmDialogPositiveText,
        				mConfirmDialogPositive);
        		
        		mConfirmDialog = mConfirmDialogBuilder.create();
        		
        		mConfirmDialog.show();
        		mConfirmDialogVisible = true;
        		break;
				
			case EVENT_ENABLE_CHOOSE_FILE_BUTTON:
				((Button)mActivity.get().findViewById(R.id.choose_file)).setEnabled(true);
				break;
				
			case EVENT_CLOSE_PROGRESS_DIALOG:
		        mProgressDialogVisible = false;
		        mProgressDialog.dismiss();
		        break;
				
			default:
				super.handleMessage(msg);
				break;
			}
		}
	};
	
	public static int run_command(String[] command, String[] environment, File cwd) {
		try {
			ProcessBuilder pb = new ProcessBuilder(Arrays.asList(command));
			if (environment != null) {
				for (String s : environment) {
					String[] split = s.split("=");
					pb.environment().put(split[0], split[1]);
				}
			}
			if (cwd != null) {
				pb.directory(cwd);
			}
			Process p = pb.start();
			//Process p = Runtime.getRuntime().exec(command, environment, cwd);
	        stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
            stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            
            // Read stdout and stderr at the same time
            Thread stdout_reader = new Thread() {
				public void run() {
					String s;
					try {
						while ((s = stdout.readLine()) != null) {
							android.util.Log.e("STDOUT", s);
							Message updateOutput = mHandler.obtainMessage(EVENT_UPDATE_PROGRESS_MSG, s);
							updateOutput.sendToTarget();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			
			Thread stderr_reader = new Thread() {
				public void run() {
					String s;
					try {
						while ((s = stderr.readLine()) != null) {
							android.util.Log.e("STDERR", s);
							Message updateOutput = mHandler.obtainMessage(EVENT_UPDATE_PROGRESS_MSG, s);
							updateOutput.sendToTarget();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			};
			
			stdout_reader.start();
			stderr_reader.start();
			stdout_reader.join();
			stderr_reader.join();
			p.waitFor();
			return p.exitValue();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return -1;
	}
}