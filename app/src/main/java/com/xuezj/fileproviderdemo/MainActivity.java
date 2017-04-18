package com.xuezj.fileproviderdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;

public class MainActivity extends BaseActivity {
    private ImageButton scanning;
    private Button camera;
    private RoundImageView roundImageView;
    public final String USER_IMAGE_NAME = "image.png";
    public final String USER_CROP_IMAGE_NAME = "temporary.png";
    public Uri imageUriFromCamera;
    public Uri cropImageUri;
    public final int GET_IMAGE_BY_CAMERA_U = 5001;
    public final int CROP_IMAGE_U = 5003;

    @Override
    protected void onResume() {

        onPermissionRequests(Manifest.permission.WRITE_EXTERNAL_STORAGE, new OnBooleanListener() {
            @Override
            public void onClick(boolean bln) {
                if (bln) {

                } else {
                    Toast.makeText(MainActivity.this, "文件读写或无法正常使用", Toast.LENGTH_SHORT).show();
                }
            }
        });
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        scanning = (ImageButton) findViewById(R.id.scanning);
        camera = (Button) findViewById(R.id.button);
        roundImageView = (RoundImageView) findViewById(R.id.userRoundImage);
        /*
        * 这里做了一下高版本权限申请的示例，BaseActivity中有两个方法onRequestPermissionsResult，onPermissionRequests
        * 各位可以自己拷贝一下就行，然后下面做了一下调用相机实例，如果你有其他的权限需要申请，可以把onPermissionRequests
        * 方法第一个参数改一下，建议申请权限在你将要使用的时候再去申请，放在一起申请可能会出错。
        * */

        scanning.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {  // N以上的申请权限实例
                    Log.d("MainActivity", "进入权限");
                    onPermissionRequests(Manifest.permission.CAMERA, new OnBooleanListener() {
                        @Override
                        public void onClick(boolean bln) {

                            if (bln) {
                                Log.d("MainActivity", "进入权限11");
                                Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
                                startActivity(intent);
                            } else {
                                Toast.makeText(MainActivity.this, "扫码拍照或无法正常使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
                    startActivity(intent);
                }
            }
        });
        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("MainActivity", "进入点击");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {  // 或者 android.os.Build.VERSION_CODES.KITKAT这个常量的值是19

                    onPermissionRequests(Manifest.permission.CAMERA, new OnBooleanListener() {
                        @Override
                        public void onClick(boolean bln) {
                            if (bln) {
                                Log.d("MainActivity", "进入权限");
                                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                                File photoFile = createImagePathFile(MainActivity.this);
                                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);

                                /*
                                * 这里就是高版本需要注意的，需用使用FileProvider来获取Uri，同时需要注意getUriForFile
                                * 方法第二个参数要与AndroidManifest.xml中provider的里面的属性authorities的值一致
                                * */
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                imageUriFromCamera = FileProvider.getUriForFile(MainActivity.this,
                                        "com.xuezj.fileproviderdemo.fileprovider", photoFile);
                                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUriFromCamera);

                                startActivityForResult(intent, GET_IMAGE_BY_CAMERA_U);
                            } else {
                                Toast.makeText(MainActivity.this, "扫码拍照或无法正常使用", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

                } else {
                    imageUriFromCamera = createImagePathUri(MainActivity.this);
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT,
                            imageUriFromCamera);
                    startActivityForResult(intent, GET_IMAGE_BY_CAMERA_U);
                }

            }
        });
    }

    public Uri createImagePathUri(Activity activity) {
        //文件目录可以根据自己的需要自行定义
        Uri imageFilePath;
        File file = new File(activity.getExternalCacheDir(), USER_IMAGE_NAME);
        imageFilePath = Uri.fromFile(file);
        return imageFilePath;
    }

    public File createImagePathFile(Activity activity) {
        //文件目录可以根据自己的需要自行定义
        Uri imageFilePath;
        File file = new File(activity.getExternalCacheDir(), USER_IMAGE_NAME);
        imageFilePath = Uri.fromFile(file);
        return file;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub

        super.onActivityResult(requestCode, resultCode, data);
        System.out.println(requestCode);
        System.out.println("数据" + resultCode + "" + this.RESULT_OK);
        if (resultCode != this.RESULT_CANCELED) {
            switch (requestCode) {
                case GET_IMAGE_BY_CAMERA_U:
                    /*
                    * 这里我做了一下调用系统切图，高版本也有需要注意的地方
                    * */
                    if (imageUriFromCamera != null) {
                        cropImage(imageUriFromCamera, 1, 1, CROP_IMAGE_U);
                        break;
                    }
                    break;
                case CROP_IMAGE_U:
                    final String s = getExternalCacheDir() + "/" + USER_CROP_IMAGE_NAME;

                    Bitmap imageBitmap = GetBitmap(s, 320, 320);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    imageBitmap.compress(Bitmap.CompressFormat.PNG, 70, baos);
                    roundImageView.setImageBitmap(imageBitmap);

                    break;
                default:
                    break;
            }
        }

    }

    public void cropImage(Uri imageUri, int aspectX, int aspectY,
                          int return_flag) {
        File file = new File(this.getExternalCacheDir(), USER_CROP_IMAGE_NAME);
        Intent intent = new Intent("com.android.camera.action.CROP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //高版本一定要加上这两句话，做一下临时的Uri
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            FileProvider.getUriForFile(MainActivity.this, "com.xuezj.fileproviderdemo.fileprovider", file);
        }
        cropImageUri = Uri.fromFile(file);

        intent.setDataAndType(imageUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", aspectX);
        intent.putExtra("aspectY", aspectY);
        intent.putExtra("outputX", 320);
        intent.putExtra("outputY", 320);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropImageUri);

        startActivityForResult(intent, return_flag);
    }

    public Bitmap GetBitmap(String path, int w, int h) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        BitmapFactory.decodeFile(path, opts);
        int width = opts.outWidth;
        int height = opts.outHeight;
        float scaleWidth = 0.f, scaleHeight = 0.f;
        if (width > w || height > h) {
            scaleWidth = ((float) width) / w;
            scaleHeight = ((float) height) / h;
        }
        opts.inJustDecodeBounds = false;
        float scale = Math.max(scaleWidth, scaleHeight);
        opts.inSampleSize = (int) scale;
        WeakReference<Bitmap> weak = new WeakReference<Bitmap>(
                BitmapFactory.decodeFile(path, opts));
        return Bitmap.createScaledBitmap(weak.get(), w, h, true);
    }
}
