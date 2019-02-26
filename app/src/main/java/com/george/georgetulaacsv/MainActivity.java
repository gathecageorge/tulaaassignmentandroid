package com.george.georgetulaacsv;

import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private static final int FILE_SELECT_CODE = 0;
    private static final String TAG = "CHOOSE_FILE";
    private Uri fileUri;
    private String upload_URL = "http://192.168.1.106:8080/csv";
    private RequestQueue rQueue;
    private ProgressDialog pd;
    private EditText txtUrl;
    private TextView txtResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtUrl = findViewById(R.id.txtUrl);
        txtResponse = findViewById(R.id.txtResponse);
        txtUrl.setText(upload_URL);
    }

    public void selectCSV(View view) {
        showFileChooser();
    }

    public void uploadCSV(View view) {
        try {
            byte[] fileData = getFileData();
            processFile(fileData);
        }catch (Exception e){
            Toast.makeText(this, "Please select CSV file first", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == RESULT_OK) {
                    // Get the Uri of the selected file
                    fileUri = data.getData();
                    Log.d(TAG, "File Uri: " + fileUri.toString());

                    Log.d(TAG, "File Name: " + getFileName());
                    ((TextView)findViewById(R.id.txtMessage)).setText("File: " + getFileName());
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private String getFileName(){
        String result = null;
        if (fileUri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(fileUri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
            }
        }
        if (result == null) {
            result = fileUri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a CSV File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void processFile(final byte[] fileData) {
        upload_URL = txtUrl.getText().toString();

        pd = new ProgressDialog(this);
        pd.setTitle("Sending File");
        pd.setMessage("Uploading file to "+upload_URL + "...");
        pd.setCancelable(false);
        pd.show();

        VolleyMultipartRequest volleyMultipartRequest = new VolleyMultipartRequest(Request.Method.POST, upload_URL,
                new Response.Listener<NetworkResponse>() {
                    @Override
                    public void onResponse(NetworkResponse response) {
                        pd.dismiss();
                        String responseString = new String(response.data);
                        Log.d(TAG, responseString);
                        rQueue.getCache().clear();
                        try {
                            JSONObject jsonObject = new JSONObject(responseString);
                            String responseMessage = "Has Error: " + jsonObject.getBoolean("error") + "\n";
                            responseMessage += "Response Message: " + jsonObject.getString("message") + "\n\n";

                            responseMessage += "Rows breakdown\n";
                            for (int i=2; i < jsonObject.length(); i++){
                                responseMessage += "Line "+(i-1)+": " + jsonObject.getString("line" + (i-1)) + "\n";
                            }

                            txtResponse.setText(responseMessage);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        pd.dismiss();
                        Toast.makeText(getApplicationContext(), "Error Occured: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }) {

            /*
             * If you want to add more parameters with the image
             * you can do it here
             * here we have only one parameter with the image
             * which is tags
             * */
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                // params.put("tags", "ccccc");  add string parameters
                return params;
            }

            /*
             *pass files using below method
             * */
            @Override
            protected Map<String, DataPart> getByteData() {
                Map<String, DataPart> params = new HashMap<>();
                long filename = System.currentTimeMillis();
                params.put("file", new DataPart(getFileName(), fileData));
                return params;
            }
        };


        volleyMultipartRequest.setRetryPolicy(
            new DefaultRetryPolicy(
                0,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        );
        rQueue = Volley.newRequestQueue(MainActivity.this);
        rQueue.add(volleyMultipartRequest);
    }

    public byte[] getFileData() throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(fileUri);
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

        // this is storage overwritten on each iteration with bytes
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        // we need to know how may bytes were read to write them to the byteBuffer
        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }

        // and then we can return your byte array.
        return byteBuffer.toByteArray();
    }
}
