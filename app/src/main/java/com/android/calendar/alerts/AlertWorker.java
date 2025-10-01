package com.android.calendar.alerts;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ForegroundInfo;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import ws.xsoh.etar.R;

/**
 * Worker class to handle calendar alerts processing.
 * Runs as a Foreground Worker to ensure time-critical tasks are not killed.
 */
public class AlertWorker extends ListenableWorker {

    public static final String KEY_ACTION = "action";
    private static final int FOREGROUND_NOTIFICATION_ID = 1337; // Make sure this ID is unique
    private static final String TAG = "AlertWorker";

    private final ExecutorService mBackgroundExecutor = Executors.newSingleThreadExecutor();
    private final AtomicReference<CallbackToFutureAdapter.Completer<Result>> mWorkCompleter = new AtomicReference<>();

    public AlertWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Creates the ForegroundInfo required to run the worker in the foreground.
     * This method is responsible for creating the notification channel and the notification.
     *
     * @return A ListenableFuture that resolves to ForegroundInfo or an exception if creation fails.
     */
    @NonNull
    private ListenableFuture<ForegroundInfo> createForegroundInfoAsync() {
        return CallbackToFutureAdapter.getFuture(completer -> {
            mBackgroundExecutor.execute(() -> {
                try {
                    Log.d(TAG, "Creating foreground info...");
                    AlertService.createChannels(getApplicationContext());
                    Notification notification = AlertService.buildForegroundNotification(
                            getApplicationContext(),
                            getApplicationContext().getString(R.string.foreground_notification_title),
                            R.drawable.stat_notify_refresh_events,
                            AlertService.FOREGROUND_CHANNEL_ID
                    );
                    Log.d(TAG, "Foreground info created successfully.");
                    int foregroundServiceTypeForInfo;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        foregroundServiceTypeForInfo = ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED;
                    } else {
                        foregroundServiceTypeForInfo = 0;
                    }
                    completer.set(new ForegroundInfo(FOREGROUND_NOTIFICATION_ID, notification, foregroundServiceTypeForInfo));

                } catch (Exception e) {
                    // Log the full exception that occurred during notification/channel creation
                    Log.e(TAG, "Failed to create ForegroundInfo for AlertWorker.", e);
                    completer.setException(e); // Propagate the specific exception
                }
            });
            // This string is used for debugging purposes by CallbackToFutureAdapter.
            return "createForegroundInfoAsync for AlertWorker";
        });
    }

    @NonNull
    @Override
    public ListenableFuture<Result> startWork() {
        ListenableFuture<Result> workFuture = CallbackToFutureAdapter.getFuture(completer -> {
            mWorkCompleter.set(completer); // Store completer for cancellation

            ListenableFuture<ForegroundInfo> foregroundInfoFuture = createForegroundInfoAsync();

            foregroundInfoFuture.addListener(() -> {
                try {
                    if (isStopped()) { // Check if work is already stopped
                        Log.i(TAG, "Work cancelled before starting foreground service.");
                        completer.set(Result.failure());
                        return;
                    }

                    ForegroundInfo foregroundInfo = foregroundInfoFuture.get();

                    // Set the worker to run in the foreground
                    // This returns a ListenableFuture<Void>, which we can listen to for success/failure
                    // of the setForegroundAsync operation itself.
                    ListenableFuture<Void> setForegroundFuture = setForegroundAsync(foregroundInfo);

                    setForegroundFuture.addListener(() -> {
                        try {
                            setForegroundFuture.get();
                            Log.d(TAG, "Successfully set worker to run in foreground. Starting actual work.");

                            // Proceed with the actual work on the background thread
                            performActualWork(completer);

                        } catch (Throwable e) {
                            Log.e(TAG, "Failed to set worker to foreground or foregrounding was cancelled.", e);
                            completer.set(Result.failure());
                        }
                    }, mBackgroundExecutor);

                } catch (Throwable e) {
                    Log.e(TAG, "Failed to obtain ForegroundInfo. Worker cannot start in foreground.", e);
                    completer.set(Result.failure());
                }
            }, mBackgroundExecutor); // Run listener on background executor

            // This string is used for debugging purposes by CallbackToFutureAdapter.
            return "startWork for AlertWorker";
        });

        // Add a listener to handle WorkManager cancellation
        workFuture.addListener(() -> {
            if (isStopped()) {
                Log.i(TAG, "Work future cancelled. Shutting down executor and cleaning up.");
                // Ensure any ongoing work in mBackgroundExecutor is stopped if possible.
                mBackgroundExecutor.shutdownNow();
            }
        }, Runnable::run);

        return workFuture;
    }

    private void performActualWork(@NonNull CallbackToFutureAdapter.Completer<Result> completer) {
        mBackgroundExecutor.execute(() -> {
            if (isStopped()) {
                Log.i(TAG, "Work cancelled before actual work execution.");
                completer.set(Result.failure());
                return;
            }

            Log.d(TAG, "Performing actual alert processing work.");
            String action = getInputData().getString(AlertWorker.KEY_ACTION);
            if (TextUtils.isEmpty(action)) {
                Log.e(TAG, "Missing action in input data for AlertWorker.");
                completer.set(Result.failure());
                return;
            }

            try {
                // Delegate the entire logic to the static AlertService class
                AlertService.handleAction(getApplicationContext(), action);
                Log.d(TAG, "Alert action '" + action + "' processed successfully.");
                if (!isStopped()) {
                    completer.set(Result.success());
                } else {
                    Log.i(TAG, "Work cancelled during actual work execution.");
                    completer.set(Result.failure());
                }
            } catch (Exception e) {
                Log.e(TAG, "Error while processing alert action: " + action, e);
                if (!isStopped()) {
                    completer.set(Result.retry());
                } else {
                    Log.i(TAG, "Work cancelled during actual work execution after an error.");
                    completer.set(Result.failure());
                }
            }
        });
    }

    @Override
    public void onStopped() {
        super.onStopped();
        Log.i(TAG, "onStopped called for AlertWorker. Attempting to interrupt background work.");
        CallbackToFutureAdapter.Completer<Result> completer = mWorkCompleter.getAndSet(null);
        if (completer != null) {
            completer.set(Result.failure());
        }
        mBackgroundExecutor.shutdownNow();
    }
}
