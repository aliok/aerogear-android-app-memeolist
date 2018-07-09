package org.aerogear.mobile.sync;

import com.apollographql.apollo.ApolloCall;
import com.apollographql.apollo.ApolloClient;
import com.apollographql.apollo.ApolloSubscriptionCall;
import com.apollographql.apollo.api.Mutation;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.Query;
import com.apollographql.apollo.api.Response;
import com.apollographql.apollo.api.Subscription;
import com.apollographql.apollo.exception.ApolloException;
import com.apollographql.apollo.subscription.WebSocketSubscriptionTransport.Factory;

import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.reactive.Request;
import org.aerogear.mobile.core.reactive.Requester;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;

import okhttp3.OkHttpClient;

import static org.aerogear.mobile.core.utils.SanityCheck.nonNull;

public class SyncService {

    private static SyncService instance;

    private final ApolloClient apolloClient;

    public SyncService(@Nonnull String serverUrl, @Nonnull String webSocketUrl) {
        nonNull(serverUrl, "serverUrl");
        nonNull(webSocketUrl, "webSocketUrl");

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

    public SyncQuery query(@Nonnull Query query) {
        return new SyncQuery(this.apolloClient, nonNull(query, "query"));
    }

    public SyncMutation mutation(@Nonnull Mutation mutation) {
        return new SyncMutation(this.apolloClient, nonNull(mutation, "mutation"));
    }

    public SyncSubscription subscribe(@Nonnull Subscription subscription) {
        return new SyncSubscription(this.apolloClient, nonNull(subscription, "subscription"));
    }

    public static class SyncQuery {

        private final ApolloClient apolloClient;
        private final Query query;

        public SyncQuery(@Nonnull ApolloClient apolloClient, @Nonnull Query query) {
            this.apolloClient = nonNull(apolloClient, "apolloClient");
            this.query = nonNull(query, "query");
        }

        public <T extends Operation.Data> Request<Response<T>> execute(Class<T> responseDataClass) {

            nonNull(responseDataClass, "responseDataClass");

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

    public static class SyncMutation {

        private final ApolloClient apolloClient;
        private final Mutation mutation;

        public SyncMutation(@Nonnull ApolloClient apolloClient, @Nonnull Mutation mutation) {
            this.apolloClient = nonNull(apolloClient, "apolloClient");
            this.mutation = nonNull(mutation, "mutation");
        }

        public <T extends Operation.Data> Request<Response<T>> execute(Class<T> responseDataClass) {

            nonNull(responseDataClass, "responseDataClass");

            return Requester.call(() -> {
                CountDownLatch latch = new CountDownLatch(1);

                final AtomicReference<Response<T>> responseData = new AtomicReference();

                apolloClient
                        .mutate(mutation)
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

    public static class SyncSubscription {

        private final ApolloClient apolloClient;
        private final Subscription subscription;

        public SyncSubscription(@Nonnull ApolloClient apolloClient, @Nonnull Subscription subscription) {
            this.apolloClient = nonNull(apolloClient, "apolloClient");
            this.subscription = nonNull(subscription, "subscription");
        }

        public <T extends Operation.Data> Request<Response<T>> execute(Class<T> responseDataClass) {

            nonNull(responseDataClass, "responseDataClass");

            return Requester.call(() -> {
                final AtomicReference<Response<T>> responseData = new AtomicReference();

                apolloClient
                        .subscribe(subscription)
                        .execute(new ApolloSubscriptionCall.Callback() {
                            @Override
                            public void onResponse(@Nonnull Response response) {
                                responseData.set(response);
                            }

                            @Override
                            public void onFailure(@Nonnull ApolloException e) {
                                throw e;
                            }

                            @Override
                            public void onCompleted() {
                            }
                        });

                return responseData.get();

            }).requestOn(new AppExecutors().networkThread());

        }
    }

}
