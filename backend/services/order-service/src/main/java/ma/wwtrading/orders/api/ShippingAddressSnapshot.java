package ma.wwtrading.orders.api;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Embeddable
public class ShippingAddressSnapshot {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @Size(max = 150)
    private String company;

    @NotBlank
    @Size(max = 30)
    private String phone;

    @NotBlank
    @Size(max = 255)
    private String addressLine1;

    @Size(max = 255)
    private String addressLine2;

    @NotBlank
    @Size(max = 100)
    private String city;

    @NotBlank
    @Size(max = 20)
    private String postalCode;

    @NotBlank
    @Size(max = 100)
    private String country;

    protected ShippingAddressSnapshot() {
    }

    public ShippingAddressSnapshot(String firstName, String lastName, String company, String phone,
                                  String addressLine1, String addressLine2, String city,
                                  String postalCode, String country) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.company = company;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.postalCode = postalCode;
        this.country = country;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getCompany() { return company; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getPostalCode() { return postalCode; }
    public String getCountry() { return country; }

    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String company() { return company; }
    public String phone() { return phone; }
    public String addressLine1() { return addressLine1; }
    public String addressLine2() { return addressLine2; }
    public String city() { return city; }
    public String postalCode() { return postalCode; }
    public String country() { return country; }
}
