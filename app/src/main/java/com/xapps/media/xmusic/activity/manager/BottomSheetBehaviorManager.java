package com.xapps.media.xmusic.activity.manager;
import com.xapps.media.xmusic.databinding.ActivityMainBinding;
import com.xapps.media.xmusic.models.BottomSheetBehavior;

public class BottomSheetBehaviorManager {

    private ActivityMainBinding binding;
	private BottomSheetBehavior bsb1;
	private BottomSheetBehavior bsb2;

    public BottomSheetBehaviorManager(ActivityMainBinding b, BottomSheetBehavior b1, BottomSheetBehavior b2) {
		this.binding = b;
		this.bsb1 = b1;
		this.bsb2 = b2;
	}

    public void setupMainBottomSheet() {
		
	}
    
}
