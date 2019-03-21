package cn.skyui.library.utils.oss;

import android.app.Application;
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

public class OssClient {

    private static final String HTTP_DOMAIN_NAME = "http://";
    private static final String HTTPS_DOMAIN_NAME = "https://";
    // oss-cn-beijing.aliyuncs.com
    private static String endpoint;
    // astatic
    private static String bucketName;
    private static Application application;
    private static ITokenProvider tokenProvider;

    private static OSS oss;

    public interface ITokenProvider {
        String getToken();
    }

    /**
     * 初始化配置信息
     * @param _endpoint 节点
     * @param _bucketName 储存区
     * @param _application 上下文
     * @param _tokenProvider http同步方法返回token
     */
    public static void init(String _endpoint,
                            String _bucketName,
                            Application _application,
                            ITokenProvider _tokenProvider) {
        endpoint = _endpoint;
        if(endpoint.startsWith(HTTP_DOMAIN_NAME)) {
            endpoint = endpoint.replaceFirst(HTTP_DOMAIN_NAME, "");
        } else if(endpoint.startsWith(HTTPS_DOMAIN_NAME)) {
            endpoint = endpoint.replaceFirst(HTTPS_DOMAIN_NAME, "");
        }
        bucketName = _bucketName;
        application = _application;
        tokenProvider = _tokenProvider;
        initOSS();
    }

    /**
     * 初始化OSSClient
     */
    private static void initOSS() {
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
        oss = new OSSClient(application, endpoint, ossFederationCredentialProvider);
    }

    private static OSSFederationToken getToken() {
        try {
            String tokenJsonStr = tokenProvider.getToken();
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

    /**
     * 异步上传文件
     * @param file 文件
     * @param callback 在主线程回调
     * @return 上传的异步任务实例，可以用于取消等操作
     */
    public static OSSAsyncTask asyncUpload(File file, final UploadCallback callback) {
        if(oss == null) {
            Log.e("oss", "oss client not init!");
            return null;
        }

        if(file == null) {
            return null;
        }

        // 构造上传请求
        PutObjectRequest put = new PutObjectRequest(bucketName, file.getName(), file.getAbsolutePath());

        // 异步上传时可以设置进度回调
        put.setProgressCallback(new OSSProgressCallback<PutObjectRequest>() {
            @Override
            public void onProgress(PutObjectRequest request, long currentSize, long totalSize) {
                Log.d("oss","PutObject = " + "currentSize: " + currentSize + " totalSize: " + totalSize);
                callback.handleProgressMessage(request.getObjectKey(), currentSize, totalSize);
            }
        });
        OSSAsyncTask task = oss.asyncPutObject(put, new OSSCompletedCallback<PutObjectRequest, PutObjectResult>() {
            @Override
            public void onSuccess(PutObjectRequest request, PutObjectResult result) {
                Log.d("oss","PutObject = " + request.getObjectKey());
                Log.d("oss","ETagt = " + result.getETag());
                Log.d("oss","RequestIdt = " + result.getRequestId());
                String url = HTTP_DOMAIN_NAME + bucketName + endpoint + request.getObjectKey();
                Log.d("oss","url = " + url);
                callback.sendSuccessMessage(url);
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
                callback.sendFailureMessage(request.getObjectKey(), serviceException);
            }
        });

        return task;
    }

}
