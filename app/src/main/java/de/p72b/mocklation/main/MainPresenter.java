package de.p72b.mocklation.main;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.room.Room;
import de.p72b.locator.location.ILastLocationListener;
import de.p72b.locator.location.ISettingsClientResultListener;
import de.p72b.locator.location.LocationManager;
import de.p72b.mocklation.R;
import de.p72b.mocklation.dialog.BackgroundLocationDialog;
import de.p72b.mocklation.dialog.DialogListener;
import de.p72b.mocklation.dialog.EditLocationItemDialog;
import de.p72b.mocklation.map.MapsActivity;
import de.p72b.mocklation.service.analytics.AnalyticsService;
import de.p72b.mocklation.service.analytics.IAnalyticsService;
import de.p72b.mocklation.service.room.AppDatabase;
import de.p72b.mocklation.service.room.LocationItem;
import de.p72b.mocklation.service.setting.ISetting;
import de.p72b.mocklation.settings.DataProtectionSettingsActivity;
import de.p72b.mocklation.util.Logger;
import io.reactivex.Completable;
import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class MainPresenter implements IMainPresenter {

    private static final String TAG = MainPresenter.class.getSimpleName();

    private IMainView mView;
    private AppDatabase mDb;
    private FragmentActivity mActivity;
    private LocationManager mLocationManager;
    private ISetting mSetting;
    private CompositeDisposable mDisposables = new CompositeDisposable();
    private IMockServiceInteractor mMockServiceInteractor;
    private Disposable mDisposableDeleteItem;
    private Disposable mDisposableInsertAll;
    private LocationItem mSelectedItem;
    private Disposable mDisposableGetAll;
    private Disposable mDisposableUpdateItem;
    private IAnalyticsService mAnalyticsService;
    private List<LocationItem> mLocationItems;

    MainPresenter(FragmentActivity activity, ISetting setting, IAnalyticsService analytics, LocationManager locationManager, android.location.LocationManager androidLocationManager) {
        Logger.d(TAG, "new MainPresenter");
        mLocationManager = locationManager;
        mActivity = activity;
        mView = (IMainView) activity;
        mSetting = setting;
        mAnalyticsService = analytics;
        mDb = Room.databaseBuilder(mActivity, AppDatabase.class, AppDatabase.DB_NAME_LOCATIONS).build();
        mMockServiceInteractor = new MockServiceInteractor(mActivity, mSetting,
                new MockServiceListener(), androidLocationManager);

        mView.setPlayPauseStopStatus(mMockServiceInteractor.getState());
    }

    @Override
    public void onResume() {
        fetchAll();
    }

    @Override
    public void onDestroy() {
        Logger.d(TAG, "onDestroy");
        mDisposables.clear();
    }

    @Override
    public void locationItemPressed(final LocationItem item) {
        mSelectedItem = item;
        if (mMockServiceInteractor.isServiceRunning() && !item.getCode().equals(
                mSetting.getMockLocationItemCode())) {
            mView.showSnackbar(R.string.error_1001, R.string.stop, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mMockServiceInteractor.stopMockLocationService();
                    mSetting.saveLastPressedLocation(item.getCode());
                    mView.selectLocation(item);
                }
            }, Snackbar.LENGTH_LONG);
            return;
        }

        mSetting.saveLastPressedLocation(item.getCode());
        mView.selectLocation(item);
    }

    @Override
    public void onClick(int viewId) {
        switch (viewId) {
            case R.id.fab:
                if (!mSetting.isPrivacyStatementAccepted()) {
                    showPrivacyUpdateDialog();
                    return;
                }
                mActivity.startActivity(new Intent(mActivity, MapsActivity.class));
                break;
            case R.id.play_stop:
                onPlayStopClicked();
                break;
            case R.id.pause:
                onPauseClicked();
                break;
            case R.id.edit:
                showEditLocationItemDialog();
                break;
            case R.id.favorite:
                onFavoriteClicked();
                break;
        }
    }

    @Override
    public void locationItemRemoved(final LocationItem item) {
        if (mMockServiceInteractor.isServiceRunning() && item.getCode().equals(
                mSetting.getMockLocationItemCode())) {
            // don't remove the actual mocked location
            mLocationItems.add(item);
            handleLocationItems(mLocationItems);
            mView.showSnackbar(R.string.error_1001, R.string.stop, new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mMockServiceInteractor.stopMockLocationService();
                    mSetting.saveLastPressedLocation(item.getCode());
                    mView.selectLocation(item);
                }
            }, Snackbar.LENGTH_LONG);
            return;
        }

        Completable.fromAction(() -> mDb.locationItemDao().delete(item))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new DeleteLocationItemObserver(item));
    }

    @Override
    public void onMockPermissionsResult(int[] grantedResults) {
        mMockServiceInteractor.onMockPermissionsResult(grantedResults);
    }

    @Override
    public void onDefaultMockAppRequest(int results) {
        mMockServiceInteractor.onDefaultMockAppRequest(results);
    }

    @Override
    public void onDeveloperOptionsEnabledRequest(int results) {

    }

    private void saveLocationItem(final LocationItem item) {
        Completable.fromAction(() -> mDb.locationItemDao().insertAll(item))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new SaveLocationItemObserver(item));
    }

    private void handleLocationItems(List<LocationItem> locationItems) {
        mLocationItems = locationItems;
        if (mLocationItems.size() == 0) {
            mView.showEmptyPlaceholder();
            return;
        }

        String lastSelectedItemCode = mSetting.getLastPressedLocationCode();
        mSelectedItem = null;
        if (lastSelectedItemCode != null) {
            for (LocationItem item : mLocationItems) {
                if (lastSelectedItemCode.equals(item.getCode())) {
                    mSelectedItem = item;
                    break;
                }
            }
        }

        if (mSelectedItem == null) {
            // preselect some item
            mSelectedItem = mLocationItems.get(0);
        }

        mView.showSavedLocations(mLocationItems);
        mView.selectLocation(mSelectedItem);
    }

    private void onPauseClicked() {
        if (mSetting.getMockLocationItemCode() != null) {
            switch (mMockServiceInteractor.getState()) {
                case MockServiceInteractor.SERVICE_STATE_RUNNING:
                    mMockServiceInteractor.pauseMockLocationService();
                    break;
                case MockServiceInteractor.SERVICE_STATE_PAUSE:
                    mMockServiceInteractor.playMockLocationService();
                    break;
                case MockServiceInteractor.SERVICE_STATE_STOP:
                    // nothing to do here
                    break;
            }
            mView.setPlayPauseStopStatus(mMockServiceInteractor.getState());
        }
    }

    private void onPlayStopClicked() {
        if (!mSetting.isPrivacyStatementAccepted()) {
            showPrivacyUpdateDialog();
            return;
        }
        if (mSelectedItem == null) {
            // TODO show error missing location item to start mocking.
            return;
        }

        if (mSetting.getMockLocationItemCode() != null && mMockServiceInteractor.isServiceRunning()) {
            mMockServiceInteractor.stopMockLocationService();
            return;
        }

        mLocationManager.getLastLocation(new ILastLocationListener() {
            @Override
            public void onSuccess(@Nullable Location location) {
                if (mMockServiceInteractor.hasRequiredPermissions()) {
                    mLocationManager.deviceLocationSettingFulfilled(new ISettingsClientResultListener() {
                        @Override
                        public void onFailure(int i, @NotNull String s) {
                            mView.showSnackbar(R.string.error_1024, -1, null, Snackbar.LENGTH_LONG);
                        }

                        @Override
                        public void onSuccess() {
                            if (mMockServiceInteractor.hasLocationProviderAvailable()) {
                                trackLocationDistance(location);
                                mMockServiceInteractor.startMockLocation(mSelectedItem.getCode());
                            } else {
                                // device has no matching location provider available
                                mAnalyticsService.trackEvent(AnalyticsService.Event.ERROR_NO_LOCATION_PROVIDER_FOUND);
                                mView.showSnackbar(R.string.error_1026, -1, null, Snackbar.LENGTH_LONG);
                            }
                        }
                    }, true);
                } else {
                    showBackgroundLocationPermissionDialog();
                }
            }

            @Override
            public void onError(int i, @Nullable String s) {
                showBackgroundLocationPermissionDialog();
            }
        }, true, true, true);
    }

    private void trackLocationDistance(@Nullable Location location) {
        Location mockLocation = getLocation();
        if (location == null || mockLocation == null) {
            return;
        }
        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, mSelectedItem.getCode());
        bundle.putFloat("length", location.distanceTo(mockLocation));
        mAnalyticsService.trackEvent(AnalyticsService.Event.DISTANCE_BETWEEN_USER_LOCATION_AND_MOCK, bundle);
    }

    @Nullable
    private Location getLocation() {
        Object geometry = mSelectedItem.getGeometry();
        if (geometry instanceof LatLng) {
            Location location = new Location("gps");
            location.setLongitude(((LatLng) geometry).longitude);
            location.setLatitude(((LatLng) geometry).latitude);
            return location;
        }
        return null;
    }

    private void showBackgroundLocationPermissionDialog() {
        FragmentManager fragmentManager = mActivity.getSupportFragmentManager();
        BackgroundLocationDialog dialog = BackgroundLocationDialog.Companion.newInstance(
                new DialogListener() {
                    @Override
                    public void onAcceptClick() {
                        mMockServiceInteractor.setLocationItem(mSelectedItem.getCode());
                        mMockServiceInteractor.requestRequiredPermissions();
                    }

                    @Override
                    public void onDeclineClick() {
                        mView.showSnackbar(R.string.error_1023, -1, null, Snackbar.LENGTH_LONG);
                    }
                });
        dialog.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogFragmentTheme);
        dialog.show(fragmentManager, BackgroundLocationDialog.Companion.getTAG());
    }

    @Override
    public void showPrivacyUpdateDialog() {
        mActivity.startActivity(new Intent(mActivity, DataProtectionSettingsActivity.class));
    }

    private void showEditLocationItemDialog() {
        FragmentManager fragmentManager = mActivity.getSupportFragmentManager();
        EditLocationItemDialog dialog = EditLocationItemDialog.newInstance(
                item -> fetchAll(), mSelectedItem
        );
        dialog.setStyle(DialogFragment.STYLE_NORMAL, R.style.DialogFragmentTheme);
        dialog.show(fragmentManager, EditLocationItemDialog.TAG);
    }

    private void fetchAll() {
        mDisposableGetAll = mDb.locationItemDao().getAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new FetchAllLocationItemObserver());
        mDisposables.add(mDisposableGetAll);
    }

    private void onFavoriteClicked() {
        final Bundle bundle = new Bundle();
        bundle.putString(FirebaseAnalytics.Param.ITEM_ID, mSelectedItem.getCode());
        mAnalyticsService.trackEvent(mSelectedItem.isIsFavorite() ?
                AnalyticsService.Event.REMOVE_FAVORITE : AnalyticsService.Event.ADD_FAVORITE, bundle);
        mSelectedItem.setIsFavorite(!mSelectedItem.isIsFavorite());
        updateItem(mSelectedItem);
    }

    private void updateItem(final LocationItem item) {
        Completable.fromAction(() -> mDb.locationItemDao().updateLocationItems(item))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new UpdateLocationItemObserver(item));
    }

    private class FetchAllLocationItemObserver implements Consumer<List<LocationItem>> {
        @Override
        public void accept(List<LocationItem> locationItems) throws Exception {
            handleLocationItems(locationItems);
            mDisposables.remove(mDisposableGetAll);
        }
    }

    private class UpdateLocationItemObserver implements CompletableObserver {
        private final LocationItem mItem;

        UpdateLocationItemObserver(LocationItem item) {
            mItem = item;
        }

        @Override
        public void onSubscribe(Disposable disposable) {
            mDisposableUpdateItem = disposable;
            mDisposables.add(mDisposableUpdateItem);
        }

        @Override
        public void onComplete() {
            mDisposables.remove(mDisposableUpdateItem);
            handleLocationItems(mLocationItems);
        }

        @Override
        public void onError(Throwable e) {
            mView.showSnackbar(R.string.error_1012, R.string.snackbar_action_retry,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            updateItem(mItem);
                        }
                    }, Snackbar.LENGTH_LONG);
        }
    }

    private class SaveLocationItemObserver implements CompletableObserver {
        private final LocationItem mItem;

        SaveLocationItemObserver(LocationItem item) {
            mItem = item;
        }

        @Override
        public void onSubscribe(Disposable disposable) {
            mDisposableInsertAll = disposable;
            mDisposables.add(mDisposableInsertAll);
        }

        @Override
        public void onComplete() {
            mDisposables.remove(mDisposableInsertAll);
            mLocationItems.add(mItem);
            handleLocationItems(mLocationItems);
        }

        @Override
        public void onError(Throwable e) {
            mView.showSnackbar(R.string.error_1009,
                    R.string.snackbar_action_retry, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            saveLocationItem(mItem);
                        }
                    }, Snackbar.LENGTH_LONG);
        }
    }

    private class DeleteLocationItemObserver implements CompletableObserver {
        private final LocationItem mItem;

        DeleteLocationItemObserver(LocationItem item) {
            mItem = item;
        }

        @Override
        public void onSubscribe(Disposable disposable) {
            mDisposableDeleteItem = disposable;
            mDisposables.add(mDisposableDeleteItem);
        }

        @Override
        public void onComplete() {
            mDisposables.remove(mDisposableDeleteItem);
            mLocationItems.remove(mItem);
            handleLocationItems(mLocationItems);
            mView.showSnackbar(R.string.message_location_item_removed,
                    R.string.snackbar_action_undo, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            saveLocationItem(mItem);
                        }
                    }, Snackbar.LENGTH_LONG);
        }

        @Override
        public void onError(Throwable e) {
            mView.showSnackbar(R.string.error_1008,
                    R.string.snackbar_action_retry, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            locationItemRemoved(mItem);
                        }
                    }, Snackbar.LENGTH_LONG);
        }
    }

    private class MockServiceListener implements MockServiceInteractor.MockServiceListener {
        @Override
        public void onStart() {
            Logger.d(TAG, "MockServiceListener onStart()");
            mView.setPlayPauseStopStatus(mMockServiceInteractor.getState());
        }

        @Override
        public void onStop() {
            Logger.d(TAG, "MockServiceListener onStop()");
            mView.setPlayPauseStopStatus(mMockServiceInteractor.getState());
        }

        @Override
        public void onUpdate() {
            Logger.d(TAG, "MockServiceListener onUpdate()");
            mView.setPlayPauseStopStatus(mMockServiceInteractor.getState());
        }
    }
}
