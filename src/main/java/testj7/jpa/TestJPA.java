package testj7.jpa;

import org.hibernate.annotations.NaturalId;

import javax.persistence.*;
import java.util.HashMap;

public class TestJPA {

    @Entity
    static class User {
        @Id
        private Integer id;
        @NaturalId
        private String login;
        private String name;

        User(String login, String name) {
            this.login = login;
            this.name = name;
        }
    }

    static EntityManagerFactory entityManagerFactory =
        Persistence.createEntityManagerFactory("TestJPA", new HashMap<String, String>() {{
            put("hibernate.connection.driver_class", "org.postgresql.Driver");
            put("hibernate.connection.url", "jdbc:postgresql://localhost:5432/epw");
            put("hibernate.connection.username", "neuville");
            put("hibernate.connection.password", "toto");
            put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        }});

    public static void main(String[] args) {
        EntityManager ent = entityManagerFactory.createEntityManager();
        User user = new User("gneuvill1", "Grégoire Neuville");

        ent.getTransaction().begin();

        ent.persist(user);

        ent.getTransaction().commit();
        ent.close();
    }
}
