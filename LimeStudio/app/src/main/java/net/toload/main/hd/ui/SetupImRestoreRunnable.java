package net.toload.main.hd.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.util.Log;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.ProgressListener;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import net.toload.main.hd.Lime;
import net.toload.main.hd.R;
import net.toload.main.hd.global.LIME;
import net.toload.main.hd.global.LIMEPreferenceManager;
import net.toload.main.hd.DBServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Art Hung on 2015/4/26.
 */
public class SetupImRestoreRunnable implements Runnable {

    // Global
    private String mType = null;
    private DBServer dbsrv = null;
    private SetupImFragment mFragment = null;
    private LIMEPreferenceManager mLIMEPref;

    // Google
    private static Drive service;
    private SetupImHandler mHandler;
    private GoogleAccountCredential credential;

    // Dropbox
    private DropboxAPI<AndroidAuthSession> mdbapi;
    private FileOutputStream mFos;
    private boolean mCanceled;

    public SetupImRestoreRunnable(SetupImFragment fragment, SetupImHandler handler, String type, GoogleAccountCredential credential, DropboxAPI mdbapi) {
        this.credential = credential;
        this.mHandler = handler;
        this.mType = type;
        this.mdbapi = mdbapi;
        this.mFragment = fragment;
        this.dbsrv = new DBServer(this.mFragment.getActivity());
        this.mLIMEPref = new LIMEPreferenceManager(this.mFragment.getActivity());
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }


    public void run() {

        mHandler.showProgress(true);
        mHandler.updateProgress(this.mFragment.getResources().getString(R.string.setup_im_restore_message));

        if (mType.equals(Lime.LOCAL)) {
            try {
                dbsrv.restoreDatabase();
                mHandler.cancelProgress();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        } else if (mType.equals(Lime.GOOGLE)) {
            restoreFromGoogle();
        } else if (mType.equals(Lime.DROPBOX)) {
            File limedir = new File(LIME.LIME_SDCARD_FOLDER + File.separator);
            if(!limedir.exists()) limedir.mkdirs();
            File tempFile = new File(LIME.LIME_SDCARD_FOLDER + File.separator + LIME.DATABASE_CLOUD_TEMP);
            tempFile.deleteOnExit();

            restoreFromDropbox download = new restoreFromDropbox(mHandler, mFragment,mdbapi, LIME.DATABASE_BACKUP_NAME, tempFile);
            download.execute();
        }
    }

    private void restoreFromGoogle() {

        service = getDriveService(credential);

        List<com.google.api.services.drive.model.File> result = new ArrayList<com.google.api.services.drive.model.File>();
        try {
            int count = 0;
            boolean continueload = false;
            Drive.Files.List request = service.files().list();
            com.google.api.services.drive.model.File cloudbackupfile = null;

            // Search and get cloudbackupfile
            do {
                FileList files = request.execute();
                for (com.google.api.services.drive.model.File f : files.getItems()) {
                    if (f.getTitle().equalsIgnoreCase(Lime.GOOGLE_BACKUP_FILENAME)) {
                        cloudbackupfile = f;
                        continueload = false;
                        break;
                    }
                }
                count += files.getItems().size();
                if (!continueload || count >= Lime.GOOGLE_RETRIEVE_MAXIMUM) {
                    break;
                }
            } while (request.getPageToken() != null &&
                    request.getPageToken().length() > 0);

            if (cloudbackupfile != null) {
                //java.io.File tempfile = new java.io.File(Lime.DATABASE_FOLDER_EXTERNAL + Lime.DATABASE_CLOUD_TEMP);
                File limedir = new File(LIME.LIME_SDCARD_FOLDER + File.separator);
                if (!limedir.exists()) {
                    limedir.mkdirs();
                }
                File tempfile = new File(LIME.LIME_SDCARD_FOLDER + File.separator + LIME.DATABASE_CLOUD_TEMP);


                if (tempfile.exists()) {
                    tempfile.delete();
                }

                Log.i("LIME", cloudbackupfile.getId());
                Log.i("LIME", cloudbackupfile.getTitle());
                Log.i("LIME", cloudbackupfile.getDescription());
                Log.i("LIME", cloudbackupfile.getDownloadUrl());

                InputStream fi = downloadFile(service, cloudbackupfile);
                FileOutputStream fo = new FileOutputStream(tempfile);

                int bytesRead;
                byte[] buffer = new byte[8 * 1024];
                while ((bytesRead = fi.read(buffer)) != -1) {
                    fo.write(buffer, 0, bytesRead);
                }

                fo.close();
                Log.i("LIME", tempfile.getAbsoluteFile() + " -> " + tempfile.length());


                // Decompress tempfile
                //DBServer.decompressFile(tempfile, Lime.DATABASE_DEVICE_FOLDER, Lime.DATABASE_NAME, true);
                DBServer.restoreDatabase(tempfile.getAbsolutePath(), true);

                dbsrv.showNotificationMessage(mFragment.getResources().getString(R.string.l3_initial_cloud_restore_end));
            } else {
                dbsrv.showNotificationMessage(mFragment.getResources().getString(R.string.l3_initial_cloud_restore_error));
            }

            mLIMEPref.setParameter(Lime.DATABASE_DOWNLOAD_STATUS, "true");

        } catch (Exception e1) {
            e1.printStackTrace();
        }
        mHandler.cancelProgress();

    }

    private Drive getDriveService(GoogleAccountCredential credential) {
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(), new GsonFactory(), credential).build();
    }

    private static InputStream downloadFile(Drive service, com.google.api.services.drive.model.File file) {
        if (file.getDownloadUrl() != null && file.getDownloadUrl().length() > 0) {
            try {
                HttpResponse resp =
                        service.getRequestFactory().buildGetRequest(new GenericUrl(file.getDownloadUrl()))
                                .execute();
                return resp.getContent();
            } catch (IOException e) {
                // An error occurred.
                e.printStackTrace();
                return null;
            }
        } else {
            // The file doesn't have any content stored on Drive.
            return null;
        }
    }

    private class restoreFromDropbox extends AsyncTask<Void, Long, Boolean> {


        private SetupImHandler mHandler;
        private SetupImFragment mFragment;
        private DropboxAPI<?> mApi;
        private String mPath;
        private File mFile;

        private FileOutputStream mFos;


        private boolean mCanceled;
        private String mErrorMsg;


        public restoreFromDropbox(SetupImHandler handler, SetupImFragment fragment, DropboxAPI<?> api, String backupFile, File tempfile) {
            // We set the context this way so we don't accidentally leak activities

            mHandler =handler;
            mFragment = fragment;
            mApi = api;
            mPath = backupFile;
            mFile = tempfile;

            mHandler.updateProgress(mFragment.getText(R.string.l3_initial_dropbox_restore_start).toString());
        }

        @Override
        protected Boolean doInBackground(Void... params) {


            try {


                // mOutputStream is class level field.
                mFos = new FileOutputStream(mFile);
                //DropboxFileInfo info =
                mApi.getFile(mPath, null, mFos, new ProgressListener() {

                    @Override
                    public void onProgress(long bytes, long total) {
                        if (!mCanceled) {
                            Long[] bytess = {bytes, total};
                            publishProgress(bytess);
                        } else {
                            if (mFos != null) {
                                try {
                                    mFos.close();
                                } catch (IOException ignored) {
                                }
                            }
                        }
                    }
                });


            } catch (FileNotFoundException e) {
                //Log.e("DbExampleLog", "File not found.");
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_restore_error).toString();

            } catch (DropboxUnlinkedException e) {
                // The AuthSession wasn't properly authenticated or user unlinked.
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_authetication_failed).toString();
            } catch (DropboxPartialFileException e) {
                // We canceled the operation
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_failed).toString();
            } catch (DropboxServerException e) {
                // Server-side exception.  These are examples of what could happen,
                // but we don't do anything special with them here.
                if (e.error == DropboxServerException._304_NOT_MODIFIED) {
                    // won't happen since we don't pass in revision with metadata
                } else if (e.error == DropboxServerException._401_UNAUTHORIZED) {
                    // Unauthorized, so we should unlink them.  You may want to
                    // automatically log the user out in this case.
                } else if (e.error == DropboxServerException._403_FORBIDDEN) {
                    // Not allowed to access this
                } else if (e.error == DropboxServerException._404_NOT_FOUND) {
                    // path not found (or if it was the thumbnail, can't be
                    // thumbnailed)
                } else if (e.error == DropboxServerException._406_NOT_ACCEPTABLE) {
                    // too many entries to return
                } else if (e.error == DropboxServerException._415_UNSUPPORTED_MEDIA) {
                    // can't be thumbnailed
                } else if (e.error == DropboxServerException._507_INSUFFICIENT_STORAGE) {
                    // user is over quota
                } else {
                    // Something else
                }
                // This gets the Dropbox error, translated into the user's language
                mErrorMsg = e.body.userError;
                if (mErrorMsg == null) {
                    mErrorMsg = e.body.error;
                }
            } catch (DropboxIOException e) {
                // Happens all the time, probably want to retry automatically.
                //mErrorMsg = "Network error.  Try again.";
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_failed).toString();
            } catch (DropboxParseException e) {
                // Probably due to Dropbox server restarting, should retry
                //mErrorMsg = "Dropbox error.  Try again.";
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_failed).toString();
            } catch (DropboxException e) {
                // Unknown error
                //mErrorMsg = "Unknown error.  Try again.";
                mErrorMsg = mFragment.getText(R.string.l3_initial_dropbox_failed).toString();

            } finally {
                if (mFos != null) {
                    try {
                        mFos.close();
                        mFos = null;

                        //Download finished. Restore db now.
                        try {
                            DBServer.restoreDatabase(mFile.getAbsolutePath(),true);
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                        finally{
                            return true;
                        }
                    } catch (IOException ignored) {
                    }
                }

            }
            return false;

        }


        @Override
        protected void onProgressUpdate(Long... progress) {
            int percent = (int) (100.0 * (double) progress[0] / progress[1] + 0.5);
            mHandler.updateProgress(mFragment.getText(R.string.l3_initial_dropbox_downloading).toString() + "  " + percent + "%");
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            mHandler.cancelProgress();

            if (result) {
                DBServer.showNotificationMessage(mFragment.getText(R.string.l3_initial_dropbox_restore_end) + "");
            } else {
                DBServer.showNotificationMessage(mErrorMsg + "");
            }

        }


    }
}

