package gr.distsystems.fruiting.network;

public interface NetworkCallback<T> {

    void onSuccess(T response);
    void onError(String errorMessage);

}
