package com.cownow.rsmod.status;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 备用通道：心跳 / dump 进度。
 * 某些 ROM / targetSdk 上 ContentProvider 会被包可见性拦掉，这时被注入的进程会改发显式广播。
 */
public class StatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();

        if (StatusStore.ACTION_DUMP_STATE.equals(action)) {
            String status = intent.getStringExtra(StatusStore.EXTRA_DUMP_STATUS);
            // 没带这个 extra 就表示“不改已有结果”
            String rvas = intent.hasExtra(StatusStore.EXTRA_DUMP_RVAS)
                    ? intent.getStringExtra(StatusStore.EXTRA_DUMP_RVAS) : null;
            StatusStore.recordDump(context, status, rvas);
            return;
        }

        if (!StatusStore.ACTION_HEARTBEAT.equals(action)) {
            return;
        }
        String pkg = intent.getStringExtra(StatusStore.EXTRA_PKG);
        String process = intent.getStringExtra(StatusStore.EXTRA_PROCESS);
        boolean self = intent.getBooleanExtra(StatusStore.EXTRA_SELF, false);
        String cfg = intent.getStringExtra(StatusStore.EXTRA_CFG);
        StatusStore.record(context, pkg, process, self, cfg);
    }
}
