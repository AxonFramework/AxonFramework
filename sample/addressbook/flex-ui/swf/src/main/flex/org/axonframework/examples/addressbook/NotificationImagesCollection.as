package org.axonframework.examples.addressbook {
import mx.collections.ArrayCollection;

public class NotificationImagesCollection {
    [Embed(source="/assets/images/bomb.png")]
    public static var error:Class;
    [Embed(source="/assets/images/information.png")]
    public static var info:Class;
    [Embed(source="/assets/images/error.png")]
    public static var valid:Class;

    [Bindable]
    public static var imagesArray:ArrayCollection = new ArrayCollection([info,error,valid]);

    function NotificationImagesCollection() {
    }
}
}