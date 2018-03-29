
import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.vishwas.credit.application.Constants;
import com.vishwas.credit.application.utils.DateUtils;
import com.vishwas.credit.application.utils.Logger;
import com.vishwas.credit.application.utils.ValidationUtils;
import com.vishwas.credit.imageUpload.model.domain.ImageUploadObject;

import java.io.ByteArrayOutputStream;

/**
 * Created by Ashish on 24/11/17.
 */

public class ImageUploadService extends IntentService {
    private StorageReference storageReference;
    private Bitmap bitmap;

    public ImageUploadService() {
        super("ImageUploadService");
        FirebaseStorage.getInstance().setMaxUploadRetryTimeMillis(10000);
        storageReference = FirebaseStorage.getInstance()
                .getReference();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Logger.logError("starting imageupload");
        ImageUploadObject imageUploadObject = intent.getParcelableExtra(Constants.BUNDLE_KEYS.IMAGE_UPLOAD_OBJECT);
        initiateUpload(imageUploadObject);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void initiateUpload(ImageUploadObject imageUploadObject) {
        uploadImageFileToFireBaseBackend(imageUploadObject);
    }

    private void uploadImageFileToFireBaseBackend(final ImageUploadObject imageUploadObject) {
        Uri uri = Uri.parse(imageUploadObject.getImageUrl());
        StorageReference ticketReference = storageReference.child(imageUploadObject.getFarmerId() + "");
        final StorageReference fileReference =
                ticketReference.child(DateUtils.epochToDateTime(imageUploadObject.getTimestamp()).replaceAll("/", ""));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap = BitmapUtils.getDownsampledBitmap(this, uri, 512, 512);
        bitmap.compress(Bitmap.CompressFormat.WEBP, 25, bytes);
        String path = MediaStore.Images.Media.insertImage(this.getContentResolver(), bitmap, "CreditApp" + DateUtils.getCurrentMillis(), null);
        UploadTask uploadTask;
        if (!ValidationUtils.isEmpty(path)) {
            uri = Uri.parse(path);
            uploadTask = fileReference.putFile(uri);
        } else {
            uploadTask = fileReference.putBytes(bytes.toByteArray());
        }
        //Recycle bitmap..dont need this anymore..variables hold bytes or uri
        bitmap.recycle();
        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(final UploadTask.TaskSnapshot taskSnapshot) {

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        Logger.logError("Image Upload success");
                        imageUploadObject.setImageUrl(taskSnapshot.getDownloadUrl().toString());
                        imageUploadObject.setStatus(Constants.ImageUploadStates.IMAGE_UPLOAD_SUCCESS_STATUS);
                        sendBroadCastForImageUpload(imageUploadObject);
                    }
                });
            }
        })
                .addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onProgress(final UploadTask.TaskSnapshot taskSnapshot) {

                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Logger.logError("Image Upload in progress");
                                long bytesTransferred = taskSnapshot.getBytesTransferred();
                                long totalByteCount = taskSnapshot.getTotalByteCount();
                                long l = (bytesTransferred / totalByteCount) * 100;
                                Logger.logError("Uploaded " + bytesTransferred / 1024 + " of " + totalByteCount);
                            }
                        });
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull final Exception exception) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                imageUploadObject.setStatus(-1);
                                Logger.logError("Image upload failed " + exception.getLocalizedMessage() + " Timestamp " + imageUploadObject.getTimestamp());
                                imageUploadObject.setStatus(Constants.ImageUploadStates.IMAGE_UPLOAD_FAILED_STATUS);
                                sendBroadCastForImageUpload(imageUploadObject);
                            }
                        });

                    }
                });

    }

    private void sendBroadCastForImageUpload(ImageUploadObject imageUploadObject) {
        Intent intent = new Intent();
        intent.setAction(Constants.INTENT_ACTIONS.ACTION_IMAGE_UPLOAD_STATUS_UPDATE);
        intent.putExtra(Constants.BUNDLE_KEYS.IMAGE_UPLOAD_OBJECT, imageUploadObject);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
