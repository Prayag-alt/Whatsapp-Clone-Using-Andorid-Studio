package com.example.chatapp.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.R;
import com.example.chatapp.databinding.ItemUserRowBinding;
import com.example.chatapp.model.User;

/**
 * ListAdapter + DiffUtil for the user list in UserListActivity.
 * Never uses notifyDataSetChanged() on the whole list.
 */
public class UserListAdapter extends ListAdapter<User, UserListAdapter.UserViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    private final OnUserClickListener clickListener;

    public UserListAdapter(OnUserClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<User> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<User>() {
                @Override
                public boolean areItemsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                    return oldItem.getUid() != null
                            && oldItem.getUid().equals(newItem.getUid());
                }

                @Override
                public boolean areContentsTheSame(@NonNull User oldItem, @NonNull User newItem) {
                    boolean sameName = strEquals(oldItem.getName(), newItem.getName());
                    boolean samePhoto = strEquals(oldItem.getProfileImageUrl(),
                            newItem.getProfileImageUrl());
                    return sameName && samePhoto;
                }

                private boolean strEquals(String a, String b) {
                    if (a == null && b == null) return true;
                    if (a == null || b == null) return false;
                    return a.equals(b);
                }
            };

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserRowBinding binding = ItemUserRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new UserViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class UserViewHolder extends RecyclerView.ViewHolder {

        private final ItemUserRowBinding binding;

        UserViewHolder(ItemUserRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(User user) {
            // Name
            String name = user.getName();
            binding.txtName.setText(name != null && !name.isEmpty() ? name : "Unknown");

            // Status / email subtitle
            String status = user.getStatus();
            if (status != null && !status.isEmpty()) {
                binding.txtStatus.setText(status);
            } else {
                String email = user.getEmail();
                binding.txtStatus.setText(email != null ? email : "");
            }

            // Avatar
            String photoUrl = user.getProfileImageUrl();
            if (photoUrl != null && !photoUrl.isEmpty()) {
                Glide.with(binding.imgAvatar.getContext())
                        .load(photoUrl)
                        .circleCrop()
                        .placeholder(R.mipmap.ic_launcher_round)
                        .into(binding.imgAvatar);
            } else {
                binding.imgAvatar.setImageResource(R.mipmap.ic_launcher_round);
            }

            // Click
            itemView.setOnClickListener(v -> clickListener.onUserClick(user));
        }
    }
}
