package com.arabic.math.solver;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class CanvasFragment extends Fragment {
    private View rootView;
    private DrawViewManager drawViewManager;
    private static final String[] METHODS = new String[]{
            "", "polynomial", "differentiate", "integrate"
    };
    private int method_Selected;
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

        TextView pred_textview = rootView.findViewById(R.id.pred_textview);
        setOnClickMethods();
        classificationCallback = new Callback<Classification>() {
            @Override
            public void onResponse(@NonNull Call<Classification> call,
                                   @NonNull Response<Classification> response) {
                String pred_result = "Equation : " + response.body().getEquation()
                        + "\nMapping : " + response.body().getMapping()
                        + "\nSolution : " + response.body().getSolution()
                        + "\nError : " + response.body().getError();
                pred_textview.setText(pred_result);
            }

            @Override
            public void onFailure(@NonNull Call<Classification> call, @NonNull Throwable t) {
                Log.e("Upload error:", t.getMessage());
                String pred_result = getResources().getString(R.string.pred_textview_str) + t.getMessage();
                pred_textview.setText(pred_result);
            }
        };

        initDrawView();
        initBottomTools();
        // init methods list
        ArrayAdapter<String> methods_adapter = new ArrayAdapter<>(requireContext(), R.layout.method_list_item, METHODS);
        AutoCompleteTextView method_list = rootView.findViewById(R.id.method_menu_autocomplete);
        method_list.setAdapter(methods_adapter);
        method_Selected = 0;
        method_list.setText(METHODS[method_Selected], false);

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

    private void uploadFile(File file) {
        Retrofiter.upload_classify(file, classificationCallback, METHODS[method_Selected]);
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
        AutoCompleteTextView method_list = rootView.findViewById(R.id.method_menu_autocomplete);
        ImageButton save, gallery;
        FloatingActionButton undo, redo;

        undo = rootView.findViewById(R.id.undo_fab);
        redo = rootView.findViewById(R.id.redo_fab);
        save = rootView.findViewById(R.id.btn_save);
        gallery = rootView.findViewById(R.id.btn_gallery);
        redo.setOnClickListener(view -> drawViewManager.redo());
        undo.setOnClickListener(view -> drawViewManager.undo());
        save.setOnClickListener(view -> {
            String[] permission_needed;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                permission_needed = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET
                };
            } else {
                permission_needed = new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.INTERNET
                };
            }

            if (!PermissionHandler.checkPermissions(permission_needed, requireContext())) {
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
                requestPermissionLauncher.launch(permission_needed);
            } else {
                File imageFile = Imguru.storeImage(requireContext(), drawViewManager.save());
                uploadFile(imageFile);
            }
        });
        gallery.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startForResultFromGallery.launch(intent);
        });
        method_list.setOnItemClickListener((parent, view, position, id) -> method_Selected = position);
    }
}