package com.arabic.math.solver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.arabic.math.solver.drawview.DrawView;
import com.arabic.math.solver.drawview.DrawViewManager;
import com.arabic.math.solver.drawview.DrawViewModes;
import com.arabic.math.solver.retrofit.Classification;
import com.arabic.math.solver.retrofit.Retrofiter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class CanvasFragment extends Fragment {
    private View rootView;
    private DrawViewManager drawViewManager;
    private Locker locker;
    private String methodSelected;
    PermissionHandler<Map<String, Boolean>> multiPermissionsCallback = new PermissionHandler<>();
    private final ActivityResultLauncher<String[]> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), multiPermissionsCallback);

    Callback<Classification> classificationCallback;
    private final ActivityResultLauncher<Intent> startForResultFromGallery = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == Activity.RESULT_OK) {
            try {
                if (result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    File img_file = new File(Imguru.getPathFromUri(requireContext(), selectedImageUri));
                    uploadFile(img_file);
                }
            } catch (Exception exception) {
                Log.d("External storage error:", exception.getLocalizedMessage());
            }
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_canvas, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View mRootView, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        this.rootView = mRootView;
        locker = new Locker();

        TextView pred_textview = rootView.findViewById(R.id.pred_textview);
        rootView.findViewById(R.id.progress_bar).setVisibility(View.INVISIBLE);
        setOnClickMethods();
        classificationCallback = new Callback<Classification>() {
            @Override
            public void onResponse(@NonNull Call<Classification> call,
                                   @NonNull Response<Classification> response) {
                Resources res = getResources(null);
                StringBuilder solution_str = new StringBuilder();
                for(int i=0 ;i<response.body().getSolution().size();i++) {
                    solution_str.append(Html.fromHtml(response.body().getSolution().get(i)));
                    if(i+1 !=response.body().getSolution().size())solution_str.append(',');
                }
                String pred_result = res.getString(R.string.equation_str) + " : " + Html.fromHtml(response.body().getEquation())
                        + "\n" + res.getString(R.string.solution_str) + " : " + solution_str
                        + "\n" + res.getString(R.string.error_str) + " : " + response.body().getError();
                pred_textview.setText(pred_result);
                rootView.findViewById(R.id.progress_bar).setVisibility(locker.unlock() ? View.INVISIBLE : View.VISIBLE);
            }

            @Override
            public void onFailure(@NonNull Call<Classification> call, @NonNull Throwable t) {
                Log.e("Upload error:", t.getMessage());
                String pred_result = getResources().getString(R.string.pred_textview_str) + t.getMessage();
                pred_textview.setText(pred_result);
                rootView.findViewById(R.id.progress_bar).setVisibility(locker.unlock() ? View.INVISIBLE : View.VISIBLE);
            }
        };

        initDrawView();
        initBottomTools();
        initBottomNavDrawer();
    }

    private void initBottomNavDrawer() {
        NavigationView bottomNavDrawer = rootView.findViewById(R.id.bottom_methods_nav);
        BottomSheetBehavior<View> navBehavior = BottomSheetBehavior.from(rootView.findViewById(R.id.bottom_sheet_behavior_id));
        FloatingActionButton methodsFab = rootView.findViewById(R.id.methods_fab);
        View scrim = rootView.findViewById(R.id.scrim);
        navBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        MenuItem defaultItem = bottomNavDrawer.getMenu().getItem(0);
        defaultItem.setChecked(true);
        methodsFab.setImageDrawable(defaultItem.getIcon());
        methodSelected = defaultItem.getTitle().toString();
        bottomNavDrawer.setCheckedItem(defaultItem);

        bottomNavDrawer.setNavigationItemSelectedListener(item -> {
            methodSelected = Locale.getDefault().getLanguage().equals("en") ? item.getTitle().toString() : getMethodNameFromItem(item);
            item.setChecked(true);
            methodsFab.setImageDrawable(item.getIcon());
            navBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            return true;
        });
        methodsFab.setOnClickListener(v -> {
            navBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });


        scrim.setOnClickListener(view -> {
            navBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        });

        navBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                scrim.setVisibility(newState == BottomSheetBehavior.STATE_HIDDEN ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {

            }
        });

    }

    private Resources getResources(String local) {
        Configuration conf = requireContext().getResources().getConfiguration();
        if (local != null) {
            conf = new Configuration(conf);
            conf.setLocale(new Locale(local));
        }
        Context localizedContext = requireContext().createConfigurationContext(conf);
        return localizedContext.getResources();
    }

    //TODO find a cleaner way to do it !!
    private String getMethodNameFromItem(MenuItem item) {
        Resources res = getResources("en");
        String methodName = "";
        int id = item.getItemId();
        if (id == R.id.simplify_method) {
            methodName = res.getString(R.string.simplify_str);
        } else if (id == R.id.polynomial_method) {
            methodName = res.getString(R.string.polynomial_str);
        } else if (id == R.id.differentiate_method) {
            methodName = res.getString(R.string.differentiate_str);
        } else if (id == R.id.integrate_method) {
            methodName = res.getString(R.string.integrate_str);
        }
        return methodName;
    }

    private void initBottomTools() {
        BottomNavigationView bottom_tools_nav = rootView.findViewById(R.id.bottom_tools_nav);
        bottom_tools_nav.setSelectedItemId(R.id.pen_tool);
        bottom_tools_nav.setOnItemSelectedListener(item -> {
            int item_id = item.getItemId();
            if (item_id == R.id.move_tool) {
                drawViewManager.setMode(DrawViewModes.MOVE);
                return true;
            } else if (item_id == R.id.eraser_tool) {
                drawViewManager.setMode(DrawViewModes.ERASE);
                return true;
            } else if (item_id == R.id.pen_tool) {
                drawViewManager.setMode(DrawViewModes.DRAW);
                return true;
            }
            return false;
        });
    }

    private void initDrawView() {
        DrawView paint = rootView.findViewById(R.id.draw_view);
        FloatingActionButton undo, redo;
        undo = rootView.findViewById(R.id.undo_fab);
        redo = rootView.findViewById(R.id.redo_fab);
        this.drawViewManager = new DrawViewManager(paint).with(undo, redo);
        paint.setManager(drawViewManager);

        ViewTreeObserver vto = paint.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                paint.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int width = paint.getMeasuredWidth();
                int height = paint.getMeasuredHeight();
                paint.init(height, width);
            }
        });

    }

    private void setOnClickMethods() {
        ImageButton gallery;
        FloatingActionButton undo, redo, analyze;
        undo = rootView.findViewById(R.id.undo_fab);
        redo = rootView.findViewById(R.id.redo_fab);
        analyze = rootView.findViewById(R.id.analyze_fab);
        gallery = rootView.findViewById(R.id.btn_gallery);
        redo.setOnClickListener(view -> drawViewManager.redo());
        undo.setOnClickListener(view -> drawViewManager.undo());
        analyze.setOnClickListener(view -> {
            if (!multiPermissionsCallback.checkPermissions(requireContext())) {
                multiPermissionsCallback.setCallback(result -> {
                    if (!result.containsValue(Boolean.FALSE)) {
                        File imageFile = Imguru.storeImage(requireContext(), drawViewManager.save());
                        uploadFile(imageFile);
                    } else {
                        StringBuilder err_message = new StringBuilder("Permissions required were denied {");
                        for (Map.Entry<String, Boolean> i : result.entrySet())
                            if (!i.getValue())
                                err_message.append(i.getKey()).append(",");
                        err_message.append("}");
                        Toast.makeText(requireContext(), err_message.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
                requestPermissionLauncher.launch(multiPermissionsCallback.getPermissionsNeeded());
            } else {
                File imageFile = Imguru.storeImage(requireContext(), drawViewManager.save());
                uploadFile(imageFile);
            }
        });
        //TODO delete this after debugging
        rootView.findViewById(R.id.save_fab).setOnClickListener(v -> Imguru.storeImage(requireContext(), drawViewManager.save()));
        gallery.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startForResultFromGallery.launch(intent);
        });
    }

    private void uploadFile(File file) {
        rootView.findViewById(R.id.progress_bar).setVisibility(locker.lock() ? View.INVISIBLE : View.VISIBLE);
        byte[] bytes = Imguru.getByteArrayFromFile(requireContext(),file);
        Retrofiter.upload_classify(bytes,file.getName(), classificationCallback, methodSelected.toLowerCase());
    }


}