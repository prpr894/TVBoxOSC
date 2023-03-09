package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.NonNull;
import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 描述
 *
 * @author pj567
 * @since 2020/12/27
 */
public class ApiDialog extends BaseDialog {
    private ImageView ivQRCode;
    private TextView tvAddress;
    private EditText inputApi;
    private String algorithm = "AES";
    private EditText mEditTextUrl;
    private EditText mEditTextPwd;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            inputApi.setText((String) event.obj);
        }
    }

    public ApiDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_api);
        setCanceledOnTouchOutside(false);
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputApi = findViewById(R.id.input);
        inputApi.setText(Hawk.get(HawkConfig.API_URL, ""));
        mEditTextUrl = findViewById(R.id.input_history);
        mEditTextPwd = findViewById(R.id.input_pwd);
        mEditTextUrl.setText(Hawk.get("iKun_url", ""));
        mEditTextPwd.setText(Hawk.get("iKun_pwd", ""));
        findViewById(R.id.inputSubmit).setOnClickListener(v -> {
            String newApi = inputApi.getText().toString().trim();
            if (!newApi.isEmpty() && (newApi.startsWith("http") || newApi.startsWith("clan"))) {
                ArrayList<String> newApis = new ArrayList<>();
                newApis.add(newApi);
                addToHistory(newApis);
                listener.onchange(newApi);
                dismiss();
            }
        });
        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<>());
                if (history.isEmpty()) return;
                String current = Hawk.get(HawkConfig.API_URL, "");
                int idx = 0;
                if (history.contains(current)) idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip("历史配置列表");
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputApi.setText(value);
                        listener.onchange(value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });
        findViewById(R.id.storagePermission).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
                    Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                } else {
                    XXPermissions.with(getContext()).permission(Permission.Group.STORAGE).request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                            } else {
                                Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        });
        refreshQRCode();


        findViewById(R.id.inputHistorySubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String url = mEditTextUrl.getText().toString();
                final String pwd = mEditTextPwd.getText().toString();
                if (TextUtils.isEmpty(url)) {
                    return;
                }
                OkGo.<String>get(url).execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        Hawk.put("iKun_url", url);
                        Hawk.put("iKun_pwd", pwd);
                        parsHistoryJsonData(response);
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        String result;
                        if (response.body() == null) {
                            result = "";
                        } else {
                            result = response.body().string();
                        }
                        return result;
                    }
                });
            }
        });

    }

    private void parsHistoryJsonData(Response<String> response) {
        try {
            String pwd = mEditTextPwd.getText().toString();
            String json;
            String rlt_str = response.body();
            if (TextUtils.isEmpty(pwd)) {
                json = rlt_str;
            } else {
                byte[] bytes = Arrays.copyOf(pwd.getBytes(), 32);
                SecretKeySpec des = new SecretKeySpec(bytes, algorithm);
                Cipher cipher = Cipher.getInstance(algorithm);
                cipher.init(Cipher.DECRYPT_MODE, des);
                byte[] decrypt = cipher.doFinal(Base64.decode(rlt_str, Base64.NO_WRAP));
                json = new String(decrypt);
            }
            JSONObject object = new JSONObject(json);
            if (object.has("data")) {
                ArrayList<String> newApis = new ArrayList<>();
                JSONArray data = object.getJSONArray("data");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject o = (JSONObject) data.get(i);
                    if (o.has("url")) {
                        String newApi = o.getString("url");
                        if (!newApi.isEmpty() && (newApi.startsWith("http") || newApi.startsWith("clan"))) {
                            newApis.add(newApi);
                        }
                    }
                }
                addToHistory(newApis);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void addToHistory(ArrayList<String> newApis) {
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<>());
        for (String newApi : newApis) {
            if (!history.contains(newApi)) history.add(0, newApi);
//                    if (history.size() > 10)
//                        history.remove(10);
        }
        Hawk.put(HawkConfig.API_HISTORY, history);
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(String.format("手机/电脑扫描上方二维码或者直接浏览器访问地址\n%s", address));
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 300), AutoSizeUtils.mm2px(getContext(), 300)));
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}
