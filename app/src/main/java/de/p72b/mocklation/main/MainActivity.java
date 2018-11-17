package de.p72b.mocklation.main;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

import de.p72b.mocklation.BuildConfig;
import de.p72b.mocklation.R;
import de.p72b.mocklation.imprint.ImprintActivity;
import de.p72b.mocklation.main.mode.IAdapterListener;
import de.p72b.mocklation.main.mode.LocationListAdapter;
import de.p72b.mocklation.main.MockServiceInteractor;
import de.p72b.mocklation.main.mode.SwipeAndTouchHelper;
import de.p72b.mocklation.service.AppServices;
import de.p72b.mocklation.service.analytics.IAnalyticsService;
import de.p72b.mocklation.service.room.LocationItem;
import de.p72b.mocklation.service.setting.ISetting;
import de.p72b.mocklation.util.Logger;
import de.p72b.mocklation.util.VisibilityAnimationListener;

public class MainActivity extends AppCompatActivity implements IMainView, View.OnClickListener,
        NavigationView.OnNavigationItemSelectedListener {

private static final String TAG = MainActivity.class.getSimpleName();
    private RecyclerView mRecyclerView;
    private LocationListAdapter mAdapter = new LocationListAdapter(new AdapterListener());
    private IMainPresenter mPresenter;
    private TextView mSelectedLocationName;
    private EditText mSelectedLocationLatitude;
    private EditText mSelectedLocationLongitude;
    private ImageButton mButtonPlayStop;
    private ImageButton mButtonPausePlay;
    private View mDataView;
    private View mDataEmpty;
    private Animation mFadeInAnimation;
    private Animation mFadeOutAnimation;
    private VisibilityAnimationListener mFadeOutListener = new VisibilityAnimationListener();
    private VisibilityAnimationListener mFadeInListener = new VisibilityAnimationListener();
    private ImageButton mFavorite;
    private DrawerLayout mDrawer;
    private NavigationView mNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initViews();

        ISetting setting = (ISetting) AppServices.getService(AppServices.SETTINGS);
        IAnalyticsService analytics = (IAnalyticsService) AppServices.getService(AppServices.ANALYTICS);
        mPresenter = new MainPresenter(this, setting, analytics);
        mFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_animation);
        mFadeInAnimation.setAnimationListener(mFadeInListener);
        mFadeOutAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out_animation);
        mFadeOutAnimation.setAnimationListener(mFadeOutListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPresenter.onResume();
    }

    @Override
    protected void onDestroy() {
        mPresenter.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        Logger.d(TAG, "onRequestPermissionsResult requestCode: " + requestCode);
        switch (requestCode) {
            case MockServiceInteractor.PERMISSIONS_MOCKING: {
                mPresenter.onMockPermissionsResult(grantResults);
                return;
            }
            default:
                // do nothing
        }
    }

    @Override
    public void showSavedLocations(List<LocationItem> locationItems) {
        toggleDataViewTo(View.VISIBLE);
        mAdapter.setData(locationItems);
    }

    @Override
    public void selectLocation(LocationItem item) {
        mSelectedLocationName.setText(LocationItem.getNameToBeDisplayed(item));

        mFavorite.setBackground(getDrawable(item.isIsFavorite() ? R.drawable.ic_favorite_black_24dp : R.drawable.ic_favorite_border_black_24dp));

        Object geometry = item.getGeometry();
        if (geometry instanceof LatLng) {
            mSelectedLocationLatitude.setText(String.valueOf(((LatLng) geometry).latitude));
            mSelectedLocationLongitude.setText(String.valueOf(((LatLng) geometry).longitude));
        }
        mAdapter.flagItem(item);
    }

    @Override
    public void showEmptyPlaceholder() {
        toggleDataViewTo(View.INVISIBLE);
    }

    private void toggleDataViewTo(int state) {
        if (View.INVISIBLE == state) {
            if (mDataEmpty.getVisibility() != View.VISIBLE) {
                mFadeInListener.setViewAndVisibility(mDataEmpty, View.VISIBLE);
                mDataEmpty.startAnimation(mFadeInAnimation);
            }
            if (mDataView.getVisibility() != View.INVISIBLE) {
                mFadeOutListener.setViewAndVisibility(mDataView, View.INVISIBLE);
                mDataView.startAnimation(mFadeOutAnimation);
            }
        } else {
            if (mDataEmpty.getVisibility() != View.INVISIBLE) {
                mFadeOutListener.setViewAndVisibility(mDataEmpty, View.INVISIBLE);
                mDataEmpty.startAnimation(mFadeOutAnimation);
            }
            if (mDataView.getVisibility() != View.VISIBLE) {
                mFadeInListener.setViewAndVisibility(mDataView, View.VISIBLE);
                mDataView.startAnimation(mFadeInAnimation);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        switch(item.getItemId()) {
            case R.id.vNavFixedMode:
                break;
            case R.id.vNavRouteMode:
                Toast.makeText(this, R.string.error_1016, Toast.LENGTH_LONG).show();
                return false;
            case R.id.vNavImprint:
                startActivity(new Intent(this, ImprintActivity.class));
                break;
        }

        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onClick(View view) {
        mPresenter.onClick(view.getId());
    }

    @Override
    public void setPlayPauseStopStatus(@MockServiceInteractor.ServiceStatus int state) {
        switch(state) {
            case MockServiceInteractor.SERVICE_STATE_RUNNING:
                mButtonPlayStop.setBackgroundResource(R.drawable.ic_stop_black_24dp);
                mButtonPausePlay.setBackgroundResource(R.drawable.ic_pause_black_24dp);
                mButtonPausePlay.setVisibility(View.VISIBLE);
                break;
            case MockServiceInteractor.SERVICE_STATE_STOP:
                mButtonPlayStop.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
                mButtonPausePlay.setVisibility(View.INVISIBLE);
                break;
            case MockServiceInteractor.SERVICE_STATE_PAUSE:
                mButtonPausePlay.setBackgroundResource(R.drawable.ic_play_arrow_black_24dp);
        }
    }

    @Override
    public void showSnackbar(int message, int action, View.OnClickListener listener, int duration) {
        Snackbar snackbar = Snackbar.make(findViewById(R.id.vMainRoot), message, duration);
        if (action != -1) {
            snackbar.setAction(action, listener);
        }
        snackbar.show();
    }

    private void initViews() {
        setContentView(R.layout.activity_main);
        final Toolbar toolbar = findViewById(R.id.vToolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(R.string.title_activity_fixed_mode));
        }
        mDrawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();

        mNavigationView = findViewById(R.id.vNavView);
        mNavigationView.setNavigationItemSelectedListener(this);
        mNavigationView.setCheckedItem(R.id.vNavFixedMode);
        ((TextView) findViewById(R.id.vNavFooterItem)).setText(BuildConfig.VERSION_NAME);

        mDataView = findViewById(R.id.data_view);
        mDataEmpty = findViewById(R.id.data_empty);
        mDataView.setVisibility(View.INVISIBLE);
        mDataEmpty.setVisibility(View.INVISIBLE);

        FloatingActionButton fab = findViewById(R.id.vFab);
        fab.setOnClickListener(this);

        mSelectedLocationName = findViewById(R.id.card_view_selected_location_name);
        mSelectedLocationLatitude = findViewById(R.id.card_view_selected_location_latitude);
        mSelectedLocationLongitude = findViewById(R.id.card_view_selected_location_longitude);

        mRecyclerView = findViewById(R.id.location_list);
        mRecyclerView.setHasFixedSize(true);
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setAutoMeasureEnabled(true);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(mAdapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new SwipeAndTouchHelper(mAdapter));
        touchHelper.attachToRecyclerView(mRecyclerView);

        mButtonPlayStop = findViewById(R.id.vPlayStop);
        mButtonPlayStop.setOnClickListener(this);

        mButtonPausePlay = findViewById(R.id.pause);
        mButtonPausePlay.setOnClickListener(this);

        mFavorite = findViewById(R.id.favorite);
        mFavorite.setOnClickListener(this);

        findViewById(R.id.edit).setOnClickListener(this);

        final View root = findViewById(R.id.vMainRoot);
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                Rect rectangle = new Rect();
                getWindow().getDecorView().getWindowVisibleDisplayFrame(rectangle);
                int statusBarHeight = rectangle.top;
                int rootHeight = root.getHeight();
                int selectedLocationCardHeight = findViewById(R.id.card_view_selected_location).getHeight();
                int toolbarHeight = toolbar.getHeight();
                int padding15dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        15, getResources().getDisplayMetrics()));

                int newHeight = rootHeight - statusBarHeight - toolbarHeight - selectedLocationCardHeight - 4 * padding15dp;

                ViewGroup.LayoutParams params = mRecyclerView.getLayoutParams();
                params.height = newHeight;
                mRecyclerView.setLayoutParams(params);
            }
        });
    }

    private class AdapterListener implements IAdapterListener {

        @Override
        public void onClick(View view) {
            int position = mRecyclerView.getChildLayoutPosition(view);
            LocationItem item = mAdapter.getItemAt(position);
            Logger.d(TAG, "onClick item: " + item.getCode());
            mPresenter.locationItemPressed(item);
        }

        @Override
        public void onItemRemoved(LocationItem item) {
            Logger.d(TAG, "onItemRemoved item: " + item.getCode());
            mPresenter.locationItemRemoved(item);
        }
    }
}
