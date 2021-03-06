package com.activity.nikhilesh.dropphoto;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.Toast;

import com.adapters.dropphoto.PhotoGridAdapter;
import com.dropbox.client2.DropboxAPI.ThumbFormat;
import com.dropbox.client2.DropboxAPI.ThumbSize;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxIOException;
import com.dropbox.client2.exception.DropboxParseException;
import com.dropbox.client2.exception.DropboxPartialFileException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.exception.DropboxUnlinkedException;
import com.session.dropphoto.UserSingleton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.utils.dropphoto.GPSTracker;
import com.utils.dropphoto.UploadToDropBox;


public class PictureActivity extends AppCompatActivity {
    Button revoke,upload,gotomap;
    ListView thumbs;
    private String mCameraFileName;
    private static final int NEW_PICTURE = 1;
    UserSingleton user = UserSingleton.getInstance();
    private final String PHOTO_DIR = "/DropPhoto/";
    private final String IMAGE_DIRECTORY_NAME = "DropPhoto";
    private final static String IMAGE_FILE_NAME = "dbroulette.png";
    private FileOutputStream mFos;
    File mediaStorageDir;
    ArrayList<DropboxAPI.Entry> entries;
    private ArrayList<String> paths;
    LocationManager lm;
    Location l;
    GPSTracker gpsTracker;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picture);
        //entries = new ArrayList<DropboxAPI.Entry>();
        lm =(LocationManager)this.getSystemService(Context.LOCATION_SERVICE);

        mediaStorageDir = new File(
                Environment
                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                IMAGE_DIRECTORY_NAME);
        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(IMAGE_DIRECTORY_NAME, "Oops! Failed create "
                        + IMAGE_DIRECTORY_NAME + " directory");

            }
        }
        upload = (Button)findViewById(R.id.camera);
        upload.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                gpsTracker = new GPSTracker(PictureActivity.this);
                String city = "";
                String lati = "";
                String longi = "";
                Intent intent = new Intent();
                // Picture from camera
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

                if (gpsTracker.canGetLocation()) {
                    //Toast.makeText(getApplicationContext(), gpsTracker.getLatitude() + " " + gpsTracker.getLongitude(), Toast.LENGTH_LONG).show();
                    try {
                        Double lat = gpsTracker.getLatitude();
                        Double lng = gpsTracker.getLongitude();
                        lati = String.valueOf(lat);
                        longi = String.valueOf(lng);
                        Geocoder gcd = new Geocoder(getApplicationContext(), Locale.getDefault());
                        List<Address> addresses = gcd.getFromLocation(lat, lng, 1);
                        if (addresses.size() > 0) {
                            //Toast.makeText(getApplicationContext(), addresses.get(0).getLocality(), Toast.LENGTH_LONG).show();
                            city = addresses.get(0).getLocality();
                        }
                    }
                    catch (IOException e){
                        e.printStackTrace();
                    }
                    Date date = new Date();
                    DateFormat df = new SimpleDateFormat("yyyyMMddkkmmss", Locale.US);
                    String newPicFile = mediaStorageDir.getPath() + File.separator + city + "_" + lati + "_" + longi + "_" + df.format(date) + ".jpg";
                    //Toast.makeText(getApplicationContext(), newPicFile, Toast.LENGTH_LONG).show();
                    File outFile = new File(newPicFile);
                    mCameraFileName = outFile.toString();
                    Uri outuri = Uri.fromFile(outFile);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, outuri);
                    Log.i("Picture Activity", "Importing New Picture: " + mCameraFileName);
                    try {
                        startActivityForResult(intent, NEW_PICTURE);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(getApplicationContext(), "There doesn't seem to be a camera.", Toast.LENGTH_SHORT).show();
                    }
                    gpsTracker.stopUsingGPS();

                } else {
                    gpsTracker.showSettingsAlert();
                }


            }
        });
        thumbs = (ListView)findViewById(R.id.contents);
        thumbs.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String path = entries.get(position).path;

                    Intent in = new Intent(getApplicationContext(), ImageActivity.class);
                    in.putExtra("PATH",path);
                    in.putExtra("DIR",mediaStorageDir.getPath());
                startActivity(in);

            }
        });

        gotomap = (Button)findViewById(R.id.gomap);
        gotomap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent in = new Intent(PictureActivity.this,MapsActivity.class);
                Bundle bundle = new Bundle();
                bundle.putSerializable("Coordinates", paths );
                in.putExtras(bundle);
                startActivity(in);
            }
        });

        if(hasOnlineAccess()) {
            DownloadFromDropBox dfb = new DownloadFromDropBox(PictureActivity.this, user.getmApi(), PHOTO_DIR, thumbs, gotomap, mediaStorageDir.getPath());
            dfb.execute();
        }
        else {
            Toast.makeText(this, "Please connect to a network...!", Toast.LENGTH_LONG).show();
        }

    }

    protected boolean hasOnlineAccess() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_picture, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        if(id == R.id.airport_menuRefresh){
            if(hasOnlineAccess()) {
                DownloadFromDropBox dfb = new DownloadFromDropBox(PictureActivity.this, user.getmApi(), PHOTO_DIR, thumbs,gotomap, mediaStorageDir.getPath());
                dfb.execute();
            }
            else {
                Toast.makeText(this, "Please connect to a network...!", Toast.LENGTH_LONG).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NEW_PICTURE) {
            // return from file upload
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = null;
                if (data != null) {
                    uri = data.getData();
                }
                if (uri == null && mCameraFileName != null) {
                    uri = Uri.fromFile(new File(mCameraFileName));
                }
                File file = new File(mCameraFileName);

                if (uri != null) {
                    if(hasOnlineAccess()) {
                        UploadToDropBox upload = new UploadToDropBox(this, user.getmApi(), PHOTO_DIR, file);
                        upload.execute();

                    }
                    else {
                        Toast.makeText(this, "Please connect to a network...!", Toast.LENGTH_LONG).show();
                    }

                }
            } else {
                Log.w("Picture Activity", "Unknown Activity Result from mediaImport: "
                        + resultCode);
            }
        }
    }


    public class DownloadFromDropBox extends AsyncTask<Void, Void, Boolean> {


        private Context mContext;
        private final ProgressDialog mDialog;
        private DropboxAPI<?> mApi;
        private String mPath;
        private ListView mView;
        private Drawable mDrawable;
        private PhotoGridAdapter adapter;
        private FileOutputStream mFos;
        private Button gotomap;

        private boolean mCanceled;
        private Long mFileLen;
        private String mErrorMsg;
        private ArrayList<Drawable> thumbs;

        private String imgDirPath;

        // Note that, since we use a single file name here for simplicity, you
        // won't be able to use this code for two simultaneous downloads.
        private final static String IMAGE_FILE_NAME = "dbroulette.png";

        public DownloadFromDropBox(Context context, DropboxAPI<?> api,
                                   String dropboxPath, ListView view,Button gotomap, String imgDirPath) {
            // We set the context this way so we don't accidentally leak activities
            mContext = context.getApplicationContext();
            this.imgDirPath = imgDirPath;
            mApi = api;
            mPath = dropboxPath;
            mView = view;
            this.gotomap = gotomap;
            if(context==null){
                Log.i("HELP HERE","CONTEXT NULL");
            }

            mDialog = new ProgressDialog(context);
            mDialog.setMessage("Refreshing...");
            mDialog.setButton(ProgressDialog.BUTTON_POSITIVE, "Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    mCanceled = true;
                    mErrorMsg = "Canceled";

                    // This will cancel the getThumbnail operation by closing
                    // its stream
                    if (mFos != null) {
                        try {
                            mFos.close();
                        } catch (IOException e) {
                        }
                    }
                }
            });

            mDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                if (mCanceled) {
                    return false;
                }

                // Get the metadata for a directory
                DropboxAPI.Entry dirent = mApi.metadata(mPath, 1000, null, true, null);

                if (!dirent.isDir || dirent.contents == null) {
                    // It's not a directory, or there's nothing in it
                    mErrorMsg = "File or empty directory";
                    return false;
                }

                // Make a list of everything in it that we can get a thumbnail for
                thumbs = new ArrayList<Drawable>();
                paths = new ArrayList<String>();
                entries = new ArrayList<DropboxAPI.Entry>();
                for (DropboxAPI.Entry ent: dirent.contents) {
                    if (ent.thumbExists) {
                        // Add it to the list of thumbs we can choose from
                        //thumbs.add(ent);
                        String path = ent.path;
                        paths.add(path);
                        mFileLen = ent.bytes;
                        entries.add(ent);

                        String cachePath = mContext.getCacheDir().getAbsolutePath() + "/" + IMAGE_FILE_NAME;
                        try {
                            mFos = new FileOutputStream(cachePath);
                        } catch (FileNotFoundException e) {
                            mErrorMsg = "Couldn't create a local file to store the image";
                            return false;
                        }

                        // This downloads a smaller, thumbnail version of the file.  The
                        // API to download the actual file is roughly the same.
                        mApi.getThumbnail(path, mFos, DropboxAPI.ThumbSize.BESTFIT_960x640,
                                DropboxAPI.ThumbFormat.JPEG, null);
                        if (mCanceled) {
                            return false;
                        }
                        mDrawable = Drawable.createFromPath(cachePath);
                        thumbs.add(mDrawable);
                    }
                }

                if (mCanceled) {
                    return false;
                }

                if (thumbs.size() == 0) {
                    // No thumbs in that directory
                    mErrorMsg = "No pictures in that directory";
                    return false;
                }





                // We must have a legitimate picture
                return true;

            } catch (DropboxUnlinkedException e) {
                // The AuthSession wasn't properly authenticated or user unlinked.
            } catch (DropboxPartialFileException e) {
                // We canceled the operation
                mErrorMsg = "Download canceled";
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
                mErrorMsg = "Network error.  Try again.";
            } catch (DropboxParseException e) {
                // Probably due to Dropbox server restarting, should retry
                mErrorMsg = "Dropbox error.  Try again.";
            } catch (DropboxException e) {
                // Unknown error
                mErrorMsg = "Unknown error.  Try again.";
            }
            return false;
        }



        @Override
        protected void onPostExecute(Boolean result) {
            mDialog.dismiss();
            if (result) {
                // Set the image now that we have it
                adapter = new PhotoGridAdapter(mContext,thumbs,entries);
                adapter.notifyDataSetChanged();
                mView.setAdapter(adapter);
                if(entries.size()>0){
                    gotomap.setEnabled(true);
                }


            } else {
                // Couldn't download it, so show an error
                showToast(mErrorMsg);
            }
        }

        private void showToast(String msg) {
            Toast error = Toast.makeText(mContext, msg, Toast.LENGTH_LONG);
            error.show();
        }


    }
}
