package io.xude;

import io.xude.util.EmptySubscriber;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class FactoryToService<Req, Resp> implements Service<Req, Resp> {
    private ServiceFactory<Req, Resp> factory;

    public FactoryToService(ServiceFactory<Req, Resp> factory) {
        this.factory = factory;
    }

    @Override
    public Publisher<Resp> apply(Publisher<Req> inputs) {
        return new Publisher<Resp>() {
            @Override
            public void subscribe(Subscriber<? super Resp> subscriber) {
                final Publisher<Service<Req, Resp>> servicePublisher = factory.apply();
                servicePublisher.subscribe(new Subscriber<Service<Req, Resp>>() {
                    private Service<Req, Resp> service = null;

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        // request only one service
                        subscription.request(1);
                    }

                    @Override
                    public void onNext(Service<Req, Resp> service) {
                        this.service = service;
                        final Publisher<Resp> responses = service.apply(inputs);
                        responses.subscribe(new Subscriber<Resp>() {
                            @Override
                            public void onSubscribe(Subscription s) {
                                s.request(Long.MAX_VALUE);
                            }

                            @Override
                            public void onNext(Resp resp) {
                                subscriber.onNext(resp);
                            }

                            @Override
                            public void onError(Throwable t) {
                                subscriber.onError(t);
                            }

                            @Override
                            public void onComplete() {
                                service.close().subscribe(new EmptySubscriber<Void>());
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable t) {
                        subscriber.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                });
            }
        };
    }

    @Override
    public Publisher<Double> availability() {
        return Services.ALWAYS_AVAILABLE;
    }

    @Override
    public Publisher<Void> close() {
        return factory.close();
    }
}