package org.aerogear.mobile.sync;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport.Factory;

import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.reactive.Request;
import org.aerogear.mobile.core.reactive.Requester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;

public class SyncService {

    private static SyncService instance;

    private final ApolloClient apolloClient;

    public SyncService(String serverUrl, String webSocketUrl) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder().build();

        apolloClient = ApolloClient.builder()
                .serverUrl(serverUrl)
                .okHttpClient(okHttpClient)
                .subscriptionTransportFactory(new Factory(webSocketUrl, okHttpClient))
                .build();
    }

    public static SyncService getInstance() {
        if (instance == null) {
            // TODO replace for the URL from mobile-services.json
            String serverUrl = "https://api.graph.cool/simple/v1/cjiyvc1wa40kg011846ev0ff8";
            String webSocketUrl = "wss://subscriptions.us-west-2.graph.cool/v1/cjiyvc1wa40kg011846ev0ff8";
            instance = new SyncService(serverUrl, webSocketUrl);
        }
        return instance;
    }

    public ApolloClient getApolloClient() {
        return apolloClient;
    }

    public SyncQuery query(Query query) {
        return new SyncQuery(this.apolloClient, query);
    }

    public static class SyncQuery {

        private final ApolloClient apolloClient;
        private final Query query;

        public SyncQuery(ApolloClient apolloClient, Query query) {
            this.apolloClient = apolloClient;
            this.query = query;
        }

        public <T extends Operation.Data> Request<Response<T>> execute(Class<T> responseDataClass) {

            return Requester.call(() -> {
                CountDownLatch latch = new CountDownLatch(1);

                final AtomicReference<Response<T>> responseData = new AtomicReference();

                apolloClient
                        .query(query)
                        .enqueue(new ApolloCall.Callback<T>() {
                            @Override
                            public void onResponse(@Nonnull Response<T> response) {
                                responseData.set(response);
                                latch.countDown();
                            }

                            @Override
                            public void onFailure(@Nonnull ApolloException e) {
                                latch.countDown();
                                throw e;
                            }
                        });

                latch.await();
                return responseData.get();
            }).requestOn(new AppExecutors().networkThread());

        }
    }

}
