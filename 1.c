#include <stdio.h>

void gauss(long n, double a[n][n], double b[n]) {
    int i, j, k;
    double x[n];
    for (i = 0; i < n - 1; i++) {
        for (j = i + 1; j < n; j++) {
            double h = a[j][i] / a[i][i];
            for (k = 0; k < n; k++) {
                a[j][k] -= h * a[i][k];
            }
            b[j] -= h * b[i];
        }
    }
    for (i = n - 1; i >= 0; i--) {
        x[i] = b[i] / a[i][i];
        for (j = i - 1; j >= 0; j--) {
            b[j] -= a[j][i] * x[i];
        }
    }
    printf("Nghiem cua he phuong trinh la:\n");
    for (i = 0; i < n; i++) {
        printf("x[%d] = %.2lf\n", i, x[i]);
    }
}

int main() {
    long n, i, j, choice;
    FILE *fp = fopen("input904.txt", "r");
    if (fp == NULL) {
        printf("Loi mo file");
        return 1;
    } else {
        fscanf(fp, "%ld", &n);
        double a[n][n], b[n];
        for (i = 0; i < n; i++) {
            for (j = 0; j < n; j++) {
                fscanf(fp, "%lf", &a[i][j]);
            }
            fscanf(fp, "%lf", &b[i]);
        }
        fclose(fp);
    }
    do {
        printf("\nLua chon:\n");
        printf("1. Giai bang phuong phap gauss\n");
        printf("2. Giai bang phuong phap cramer\n");
        printf("3. Exit\n");
        printf("An phim: ");
        scanf("%ld", &choice);

        switch (choice) {
            case 1:
            	long n, i, j, choice;
    FILE *fp = fopen("input904.txt", "r");
    if (fp == NULL) {
        printf("Loi mo file");
        return 1;
    } else {
        fscanf(fp, "%ld", &n);
        double a[n][n], b[n];
        for (i = 0; i < n; i++) {
            for (j = 0; j < n; j++) {
                fscanf(fp, "%lf", &a[i][j]);
            }
            fscanf(fp, "%lf", &b[i]);
        }
        fclose(fp);
    }
                printf("Gauss:\n");
                gauss(n, a, b);
                break;
            case 2:
                printf("Cramer:\n");
                // cramer(n, a, b); // B?n dã b? ph?n này, do không c?n thi?t
                break;
            case 3:
                printf("Exiting...\n");
                return 0;
            default:
                printf("Lua chon khong dung xin chon lai !\n");
        }
    } while (choice != 3);

    return 0; // Thêm l?nh tr? v? 0 ? cu?i hàm main
}

