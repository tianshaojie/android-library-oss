package cn.skyui.library.utils.oss;

import android.util.Log;

import com.alibaba.sdk.android.oss.ClientConfiguration;
import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSS;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.callback.OSSProgressCallback;
import com.alibaba.sdk.android.oss.common.OSSLog;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationCredentialProvider;
import com.alibaba.sdk.android.oss.common.auth.OSSFederationToken;
import com.alibaba.sdk.android.oss.internal.OSSAsyncTask;
import com.alibaba.sdk.android.oss.model.PutObjectRequest;
import com.alibaba.sdk.android.oss.model.PutObjectResult;

import org.json.JSONObject;

import java.io.File;

/**
 * Created by tiansj on 15/8/31.
 */
public class UploadClient {

    public static final String IMAGE_DOMAIN = "http://oss-cn-beijing.aliyuncs.com"; // 该目录下要有两个文件：file1m  file10m
    public static final String BUCKET_NAME = "astatic";

    static OSS oss;
    static {
        initOSS();
    }

    // 初始化OSSClient
    private static void initOSS() {
        if(!OssInitManager.isIsInitialized()) {
            Log.e("oss", "oss library not init!");
        }

        OSSLog.enableLog();

        //推荐使用OSSAuthCredentialsProvider。token过期可以及时更新
        OSSFederationCredentialProvider ossFederationCredentialProvider = new OSSFederationCredentialProvider() {
            @Override
            public OSSFederationToken getFederationToken() {
               return getToken();
            }
        };

        //该配置类如果不设置，会有默认配置，具体可看该类
        ClientConfiguration conf = new ClientConfiguration();
        conf.setConnectionTimeout(15 * 1000); // 连接超时，默认15秒
        conf.setSocketTimeout(15 * 1000); // socket超时，默认15秒
        conf.setMaxConcurrentRequest(5); // 最大并发请求数，默认5个
        conf.setMaxErrorRetry(2); // 失败后最大重试次数，默认2次
        oss = new OSSClient(OssInitManager.getApplication(), IMAGE_DOMAIN, ossFederationCredentialProvider);
    }

    private static OSSFederationToken getToken() {
        try {
            String tokenJsonStr = OssInitManager.getTokenProvider().getToken();
            if(tokenJsonStr != null && tokenJsonStr.length() > 0) {
                JSONObject jsonObject =  new JSONObject(tokenJsonStr);
                String ak = jsonObject.getString("AccessKeyId");
                String sk = jsonObject.getString("AccessKeySecret");
                String securityToken = jsonObject.getString("SecurityToken");
                long expireTime = jsonObject.getLong("Expiration");
                return new OSSFederationToken(ak, sk, securityToken, expireTime);
            }
        } catch (Exception e) {
            Log.e("oss", "getToken exception: " + e.getMessage());
        }
        return null;
    }

    public static void asyncUpload(File file, final UploadCallbackHandler handler) {
        if(!OssInitManager.isIsInitialized()) {
            Log.e("oss", "oss library not init!");
        }

        if(file == null) {
            return;
        }
        // 构造上传请求
        PutObjectRequest put = new PutObjectRequest(BUCKET_NAME, file.getName(), file.getAbsolutePath());

        // 异步上传时可以设置进度回调
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                Log.d("oss","PutObject = " + "currentSize: " + currentSize + " totalSize: " + totalSize);
                handler.handleProgressMessage(request.getObjectKey(), currentSize, totalSize);
            }
        });
        OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                Log.d("oss","PutObject = " + request.getObjectKey());
                Log.d("oss","ETagt = " + result.getETag());
                Log.d("oss","RequestIdt = " + result.getRequestId());
                handler.sendSuccessMessage(request.getObjectKey());
            }
            @Override
            public void onFailure(PutObjectRequest request, ClientException clientExcepion, ServiceException serviceException) {
                // 请求异常
                if (clientExcepion != null) {
                    // 本地异常如网络异常等
                    clientExcepion.printStackTrace();
                }
                if (serviceException != null) {
                    // 服务异常
                    Log.e("oss","ErrorCode = " + serviceException.getErrorCode());
                    Log.e("oss","RequestId = " + serviceException.getRequestId());
                    Log.e("oss","HostId = " + serviceException.getHostId());
                    Log.e("oss","RawMessage = " + serviceException.getRawMessage());
                }
                handler.sendFailureMessage(request.getObjectKey(), serviceException);
            }
        });
    }

}
