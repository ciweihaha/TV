package com.fongmi.android.tv.ui.dialog;

import android.view.View;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.utils.Util;

final class PlaybackDialog {

    private PlaybackDialog() {
    }

    static void showSheet(FragmentActivity activity, DialogFragment bottom, DialogFragment side) {
        FragmentManager manager = activity.getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        for (Fragment fragment : manager.getFragments()) if (fragment.getClass() == bottom.getClass() || fragment.getClass() == side.getClass()) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) side.show(manager, null);
        else bottom.show(manager, null);
    }

    static void observe(Fragment fragment, Observer<PlayerManager> observer) {
        PlaybackActivity activity = (PlaybackActivity) fragment.requireActivity();
        View view = fragment.requireView();
        view.setVisibility(View.INVISIBLE);
        activity.getPlaybackPlayerState().observe(fragment.getViewLifecycleOwner(), player -> {
            view.setVisibility(player == null ? View.INVISIBLE : View.VISIBLE);
            observer.onChanged(player);
        });
    }

    static boolean isCurrentPlayer(Fragment fragment, PlayerManager player) {
        return player != null && fragment.isAdded() && ((PlaybackActivity) fragment.requireActivity()).getPlaybackPlayerState().getValue() == player;
    }
}
