package org.aerogear.android.app.memeolist.ui;

import android.content.Intent;
import android.databinding.BindingAdapter;
import android.databinding.ObservableArrayList;
import android.databinding.ObservableList;
import android.os.Bundle;
import android.support.v4.widget.CircularProgressDrawable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.ImageView;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.nitrico.lastadapter.LastAdapter;

import org.aerogear.android.app.memeolist.BR;
import org.aerogear.android.app.memeolist.R;
import org.aerogear.android.app.memeolist.graphql.CreateMemeSubscription;
import org.aerogear.android.app.memeolist.graphql.ListMemesQuery;
import org.aerogear.android.app.memeolist.model.Meme;
import org.aerogear.mobile.core.MobileCore;
import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.reactive.Request;
import org.aerogear.mobile.core.reactive.RequestMapFunction;
import org.aerogear.mobile.core.reactive.Requester;
import org.aerogear.mobile.core.reactive.Responder;
import org.aerogear.mobile.sync.SyncService;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MemeListActivity extends AppCompatActivity {

    @BindView(R.id.memes)
    RecyclerView mMemes;

    @BindView(R.id.swipe)
    SwipeRefreshLayout mSwipe;

    private ObservableList<Meme> memes = new ObservableArrayList<>();
    private ApolloClient apolloClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meme_list);

        ButterKnife.bind(this);

        apolloClient = SyncService.getInstance().getApolloClient();

        mMemes.setLayoutManager(new LinearLayoutManager(this));
        new LastAdapter(memes, BR.meme)
                .map(Meme.class, R.layout.item_meme)
                .into(mMemes);

        mSwipe.setOnRefreshListener(() -> retrieveMemes());

        subscription();

        retrieveMemes();
    }

    private void subscription() {

        ApolloSubscriptionCall<CreateMemeSubscription.Data> subscriptionCall = apolloClient
                .subscribe(new CreateMemeSubscription());

        subscriptionCall.execute(new ApolloSubscriptionCall.Callback<CreateMemeSubscription.Data>() {
            @Override
            public void onResponse(@Nonnull Response<CreateMemeSubscription.Data> response) {

                new AppExecutors().mainThread().submit(() -> {
                    CreateMemeSubscription.Node node = response.data().Meme().node();
                    memes.add(0, new Meme(node.id(), node.photoUrl()));
                    mMemes.smoothScrollToPosition(0);
                });

            }

            @Override
            public void onFailure(@Nonnull ApolloException e) {
                MobileCore.getLogger().error(e.getMessage(), e);
            }

            @Override
            public void onCompleted() {
            }
        });

    }

    private void retrieveMemes() {

        // Before
//        apolloClient
//                .query(ListMemesQuery.builder().build())
//                .enqueue(new ApolloCall.Callback<ListMemesQuery.Data>() {
//                    @Override
//                    public void onResponse(@Nonnull Response<ListMemesQuery.Data> response) {
//                        new AppExecutors().mainThread().submit(() -> {
//                            memes.clear();
//
//                            List<ListMemesQuery.AllMeme> allMemes = response.data().allMemes();
//
//                            for (ListMemesQuery.AllMeme meme : allMemes) {
//                                memes.add(new Meme(meme.id(), meme.photoUrl()));
//                            }
//
//                            mSwipe.setRefreshing(false);
//                        });
//                    }
//
//                    @Override
//                    public void onFailure(@Nonnull ApolloException e) {
//                        MobileCore.getLogger().error(e.getMessage(), e);
//
//                        mSwipe.setRefreshing(false);
//                    }
//                });

        // After
        SyncService
                .getInstance()
                .query(ListMemesQuery.builder().build())
                .execute(ListMemesQuery.Data.class)
                .respondOn(new AppExecutors().mainThread())
                .requestMap(responseData -> {
                    List<Meme> memes = new ArrayList<>();

                    for (ListMemesQuery.AllMeme meme : responseData.data().allMemes()) {
                        memes.add(new Meme(meme.id(), meme.photoUrl()));
                    }

                    return Requester.emit(memes);
                })
                .respondWith(new Responder<List<Meme>>() {
                    @Override
                    public void onResult(List<Meme> data) {
                        memes.clear();
                        memes.addAll(data);
                        mSwipe.setRefreshing(false);
                    }

                    @Override
                    public void onException(Exception exception) {
                        MobileCore.getLogger().error(exception.getMessage(), exception);

                        mSwipe.setRefreshing(false);
                    }
                });

    }

    @BindingAdapter("memeImage")
    public static void displayMeme(ImageView imageView, Meme meme) {
        CircularProgressDrawable placeHolder = new CircularProgressDrawable(imageView.getContext());
        placeHolder.setStrokeWidth(5f);
        placeHolder.setCenterRadius(30f);
        placeHolder.start();

        Glide.with(imageView)
                .load(meme.getPhotoUrl())
                .apply(RequestOptions.placeholderOf(placeHolder))
                .into(imageView);
    }

    @OnClick(R.id.newMeme)
    void newMeme() {
        startActivity(new Intent(this, MemeFormActivity.class));
    }

}
